/*
 * Copyright (C) 2022 Slack Technologies, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package slack.gradle.tasks

import java.io.File
import java.util.Locale
import java.util.Properties
import javax.inject.Inject
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.UntrackedTask
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.language.base.plugins.LifecycleBasePlugin
import oshi.SystemInfo
import slack.cli.AppleSiliconCompat
import slack.cli.AppleSiliconCompat.isMacOS
import slack.gradle.SlackProperties
import slack.gradle.isCi
import slack.gradle.isRootProject
import slack.gradle.jdkVersion
import slack.gradle.tasks.BootstrapPropertiesMode.APPEND
import slack.gradle.tasks.BootstrapPropertiesMode.LOG
import slack.gradle.tasks.BootstrapUtils.DaemonArgsProvider
import slack.gradle.tasks.BootstrapUtils.computeDaemonArgs
import slack.gradle.util.mapToInt

/**
 * Other bootstrap tasks can be finalized by this and/or depend on its outputs by implementing this
 * interface. Implementing this alone ensures they will be run whenever [CoreBootstrapTask] is run.
 *
 * Implementers are expected to handle internal up-to-date checks and no-op as needed.
 */
internal interface BootstrapTask : Task

/**
 * Enum indicating the different modes that [CoreBootstrapTask] can update `gradle.properties` file
 * as.
 */
public enum class BootstrapPropertiesMode {
  /** Log the properties that should be used only. */
  LOG,
  /** Append them to the target gradle.properties file. */
  APPEND,
  /** Overwrite them in the target gradle.properties file. */
  OVERWRITE
}

private val BYTES_PER_GB = 1024.0.pow(3)
private const val NEW_SIZE_PERCENT = 0.67

/** The ratio of Gradle jvm args memory to kotlin daemon memory. */
private const val DEFAULT_GRADLE_MEMORY_PERCENT = 0.50f

/**
 * The core Bootstrap task that all bootstrap-applicable tasks can depend on. This task configures
 * the local or CI developer environment for optimal gradle execution.
 */
@UntrackedTask(because = "This should always reset the current state when run.")
public abstract class CoreBootstrapTask
@Inject
constructor(objects: ObjectFactory, providers: ProviderFactory) : DefaultTask() {

  private val argsProvider = DaemonArgsProvider.fromProviders(providers)

  // Current bootstrap version
  @get:Input public abstract val bootstrapVersion: Property<Int>

  /**
   * Environments can have different constraints, namely CI vs local.
   * - In CI, we want to use near-100% of resources.
   * - In local, we want to maybe just use 50% (leaves room for studio, emulator, chrome etc)
   */
  @get:Input public abstract val ciBooleanProperty: Property<Boolean>

  @get:Input public abstract val offlineBooleanProperty: Property<Boolean>

  // Normally ~/.gradle/gradle.properties, but could also be local depending on CI needs
  @get:PathSensitive(PathSensitivity.ABSOLUTE)
  @get:InputFile
  public abstract val gradlePropertiesFile: RegularFileProperty

  /**
   * Controls how properties are handled.
   *
   * ./gradlew bootstrap -Pbootstrap.properties-mode=(LOG|APPEND|OVERWRITE)
   */
  @get:Input public abstract val propertiesMode: Property<BootstrapPropertiesMode>

  @get:Input
  public val extraJvmArgs: ListProperty<String> =
    objects.listProperty<String>().convention(argsProvider.extraJvmArgs)

  @get:OutputFile public abstract val bootstrapVersionFileProperty: RegularFileProperty

  /**
   * We require a launcher here and query it so we can let Gradle handle downloading and
   * installation of the appropriate JDKs.
   */
  @get:Optional @get:Nested public abstract val launcher: Property<JavaLauncher>

  /** If using java toolchains, this is specified to indicate the requested JDK version. */
  @get:Optional
  @get:Input
  public val minGradleXmx: Property<Int> =
    objects.property<Int>().convention(argsProvider.minGradleXmx)

  /** If using java toolchains, this is specified to indicate the requested JDK version. */
  @get:Optional @get:Input public abstract val jdkVersion: Property<Int>

  /**
   * Optional custom memory multiplier. This should be a number from 1 -> 100 and is a percentage.
   *
   * This is useful for overriding memory usage in testing.
   */
  @get:Optional
  @get:Input
  public val customMemoryMultiplier: Property<String> =
    objects.property<String>().convention(argsProvider.customMemoryMultiplier)

  /**
   * Optional custom garbage collector to use. Note that this is just for testing and will be
   * removed.
   */
  @get:Optional
  @get:Input
  public val garbageCollector: Property<String> =
    objects.property<String>().convention(argsProvider.garbageCollector)

  /**
   * Optional custom core multiplier. This should be a number from 1 -> 100 and is a percentage.
   *
   * This is useful for overriding core counts in testing.
   */
  @get:Optional
  @get:Input
  public val customCoreMultiplier: Property<String> =
    objects.property<String>().convention(argsProvider.customCoreMultiplier)

  /**
   * Optional custom Gradle memory multiplier. This should be a number from 1 -> 100 and is a
   * percentage.
   *
   * This is useful for overriding memory splitting in testing.
   */
  @get:Optional
  @get:Input
  public val gradleMemoryPercentage: Property<String> =
    objects.property<String>().convention(argsProvider.gradleMemoryPercentage)

  @get:OutputDirectory public abstract val cacheDir: DirectoryProperty

  @get:OutputFile public abstract val diagnostics: RegularFileProperty

  /** Output file of the java installation, useful for CI to point JAVA_HOME at it. */
  @get:OutputFile public abstract val javaInstallationPath: RegularFileProperty

  @TaskAction
  internal fun bootstrap() {
    check(!offlineBooleanProperty.get()) { "Bootstrap can't run in offline mode." }

    val diagnosticsOutput = StringBuilder()

    if (launcher.isPresent) {
      diagnosticsOutput.appendLine("Initializing JDK")
      diagnosticsOutput.appendLine(
        """
        JDK config:
        Installation path: ${launcher.get().metadata.installationPath.asFile.absolutePath}
        Launcher path: ${launcher.get().executablePath.asFile.absolutePath}

        Ensure your JAVA_HOME env points to the installation path or add this line to your .bashrc/.zshrc/.profile
        $ export JAVA_HOME=${launcher.get().metadata.installationPath.asFile.absolutePath}

        Restart Android Studio once to ensure this is picked up in your Project Structure as well!

        For advanced configuration see https://github.com/tinyspeck/slack-android-ng/wiki/JDK-Installation-&-JAVA_HOME

        """
          .trimIndent()
      )
      javaInstallationPath.asFile
        .get()
        .writeText(launcher.get().metadata.installationPath.asFile.absolutePath)
    }

    val isCi = ciBooleanProperty.get()

    val (gradleArgs, kotlinArgs) =
      computeDaemonArgs(
        isCi,
        customMemoryMultiplier,
        customCoreMultiplier,
        gradleMemoryPercentage,
        minGradleXmx,
        extraJvmArgs,
        garbageCollector,
        diagnosticsOutput::appendLine
      )

    val properties =
      mutableMapOf(
        "org.gradle.jvmargs" to
          "-Duser.country=US -Dfile.encoding=UTF-8 -XX:+HeapDumpOnOutOfMemoryError ${gradleArgs.args.joinToString(" ")}",
        SlackProperties.KOTLIN_DAEMON_ARGS_KEY to
          "-Duser.country=US -Dfile.encoding=UTF-8 -XX:+HeapDumpOnOutOfMemoryError ${kotlinArgs.args.joinToString(" ")}",
      )

    // To reduce thermal throttling, we cap max workers on Intel mac devices
    if (isMacOS() && AppleSiliconCompat.Arch.get() != AppleSiliconCompat.Arch.ARM64) {
      properties["org.gradle.workers.max"] = "${gradleArgs.maxWorkers}"
    }

    if (isCi) {
      // Enabled by default, disable on CI
      properties["org.gradle.vfs.watch"] = "false"
    }

    val mode = propertiesMode.get()
    val regionMarkerPrefix = "# Begin: Slack bootstrap-generated properties"
    val regionMarkerSuffix = "# End: Slack bootstrap-generated properties"
    val prefix =
      """
      $regionMarkerPrefix
      # Note that these properties will prevent you from using other projects on JDK 8
      # These are appended to the end of the file to override any previous declarations of the same
      # keys, but you should consider removing those keys if you have any.
    """
        .trimIndent()
    val suffix = "\n$regionMarkerSuffix"
    val propsString =
      properties.entries.joinToString("\n", prefix = "\n$prefix\n", postfix = suffix) { (key, value)
        ->
        "$key=$value"
      }
    if (mode == LOG) {
      diagnosticsOutput.appendLine("Computed bootstrap gradle properties:\n$propsString")
    } else {
      val propsFile = gradlePropertiesFile.asFile.get()
      if (mode == APPEND) {
        diagnosticsOutput.appendLine("Appending properties to $propsFile")
        val propsFileLines = ArrayList(propsFile.readLines())
        val existingIndex = propsFileLines.indexOf(regionMarkerPrefix)
        if (existingIndex != -1) {
          diagnosticsOutput.appendLine("Removing existing region first")
          val endIndex = propsFileLines.indexOf(regionMarkerSuffix)
          check(endIndex != -1) {
            "Could not find region suffix for properties, please delete the Slack properties below '$regionMarkerPrefix' and rerun."
          }
          propsFileLines.subList(existingIndex - 1, endIndex + 1).clear()
        }
        propsFile.writeText("${propsFileLines.joinToString("\n")}\n$propsString")
      } else {
        diagnosticsOutput.appendLine("Writing properties to $propsFile")
        // Overwrite
        val loaded = Properties()
        propsFile.bufferedReader().use(loaded::load)
        loaded.putAll(properties)
        propsFile.bufferedWriter().use { loaded.store(it, null) }
      }
    }

    bootstrapVersionFileProperty.asFile.get().writeText(VERSION.toString())
    diagnostics.asFile.get().writeText(diagnosticsOutput.toString())
  }

  public object Properties {
    public const val PROPERTIES_MODE: String = "bootstrap.properties-mode"
    public const val PROPERTIES_FILE: String = "bootstrap.properties-file"
  }

  public companion object {
    private const val NAME: String = "bootstrap"

    /**
     * Current bootstrap version. Other bootstrap tasks can check against this as a minimum version.
     */
    public const val VERSION: Int = 2

    internal fun isBootstrapEnabled(project: Project): Boolean {
      return project.gradle.startParameter.taskNames.any { it == NAME }
    }

    internal fun configureSubprojectBootstrapTasks(project: Project) {
      if (!isBootstrapEnabled(project)) return
      val rootTask = project.rootProject.tasks.named<CoreBootstrapTask>(NAME)
      // Clever trick to make this finalized by all bootstrap tasks and all other tasks depend on
      // this, so bootstrap always runs first.
      project.tasks.configureEach {
        val task = this
        if (name == NAME) return@configureEach
        if (name == LifecycleBasePlugin.CLEAN_TASK_NAME) return@configureEach
        if (this is BootstrapTask) {
          rootTask.configure { finalizedBy(task) }
        } else {
          dependsOn(rootTask)
        }
      }
    }

    public fun register(project: Project): TaskProvider<CoreBootstrapTask> {
      check(project.isRootProject) { "Bootstrap can only be applied to the root project" }
      val bootstrap =
        project.tasks.register<CoreBootstrapTask>(NAME) {
          val jdkVersion = project.jdkVersion()
          val service = project.serviceOf<JavaToolchainService>()
          val defaultLauncher =
            service.launcherFor { languageVersion.set(JavaLanguageVersion.of(jdkVersion)) }
          this.launcher.convention(defaultLauncher)
          this.jdkVersion.set(jdkVersion)

          val cacheDirProvider = project.layout.projectDirectory.dir(".cache")
          val bootstrapVersionFileProvider = cacheDirProvider.file("bootstrap.txt")
          val bootstrapVersionFile: File = bootstrapVersionFileProvider.asFile
          val version =
            if (bootstrapVersionFile.exists()) {
              bootstrapVersionFile.readText().toInt()
            } else {
              -1
            }
          bootstrapVersion.set(version)
          bootstrapVersionFileProperty.set(bootstrapVersionFileProvider)
          diagnostics.set(project.layout.buildDirectory.file("bootstrap/diagnostics.txt"))
          javaInstallationPath.set(
            project.layout.buildDirectory.file("bootstrap/javaInstallation.txt")
          )
          cacheDir.set(cacheDirProvider)
          offlineBooleanProperty.set(project.gradle.startParameter.isOffline)
          val gradleHome = project.gradle.gradleUserHomeDir
          ciBooleanProperty.set(project.isCi)

          // TODO for profiler, we want to set these to the local properties + append
          val propertiesFileProvider: () -> File = {
            project.providers
              .gradleProperty(Properties.PROPERTIES_FILE)
              .map { File(it) }
              .orElse(File(gradleHome, "gradle.properties"))
              .get()
          }
          gradlePropertiesFile.set(propertiesFileProvider)
          propertiesMode.set(
            project.providers
              .gradleProperty(Properties.PROPERTIES_MODE)
              .map { BootstrapPropertiesMode.valueOf(it.uppercase(Locale.US)) }
              .orElse(APPEND)
          )
        }

      return bootstrap
    }
  }
}

internal object BootstrapUtils {
  private val jdkOpensAndExports =
    listOf(
        // For GJF and compile-testing
        // https://github.com/diffplug/spotless/issues/834
        // https://github.com/google/google-java-format#jdk-16
        // https://github.com/google/compile-testing/issues/222 (only javac api)
        "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
        // For Gson's reflective serialization during AGP manifest merging
        // Can be removed with AGP 7.1+
        // https://issuetracker.google.com/issues/193919814
        "--add-opens=java.base/java.io=ALL-UNNAMED",
      )
      .distinct()
      .sorted()

  class DaemonArgsProvider(
    val customMemoryMultiplier: Provider<String>,
    val customCoreMultiplier: Provider<String>,
    val gradleMemoryPercentage: Provider<String>,
    val minGradleXmx: Provider<Int>,
    val extraJvmArgs: Provider<List<String>>,
    val garbageCollector: Provider<String>,
  ) {
    companion object {
      fun fromProviders(
        providers: ProviderFactory,
      ): DaemonArgsProvider {
        return DaemonArgsProvider(
          customMemoryMultiplier = providers.environmentVariable("BOOTSTRAP_MEMORY_MULTIPLIER"),
          customCoreMultiplier = providers.environmentVariable("BOOTSTRAP_CORE_MULTIPLIER"),
          gradleMemoryPercentage = providers.environmentVariable("GRADLE_MEMORY_PERCENT"),
          minGradleXmx =
            providers.gradleProperty(SlackProperties.MIN_GRADLE_XMX).mapToInt().orElse(1),
          extraJvmArgs = providers.provider { emptyList() },
          garbageCollector = providers.environmentVariable("BOOTSTRAP_GC"),
        )
      }
    }
  }

  fun computeDaemonArgs(
    isCi: Boolean,
    customMemoryMultiplier: Provider<String>,
    customCoreMultiplier: Provider<String>,
    gradleMemoryPercentage: Provider<String>,
    minGradleXmx: Provider<Int>,
    extraJvmArgs: Provider<List<String>>,
    garbageCollector: Provider<String>,
    diagnostic: (String) -> Unit
  ): DaemonArgs {

    fun <T> pickValue(ci: T, local: T): T {
      return if (isCi) ci else local
    }

    // CPU cores
    // - Always 50% of what gradle recommends for local envs.
    //   Our max parallelism on the crit path is around 8-12 workers, so no need to go ham and
    //   risk thermal throttling or pegged CPUs.
    // - 100% for normal CI
    // Memory
    // - 50% for local builds, lower bound 15%
    // - 75% for CI builds, fixed size.
    // VFS watching
    // - enabled locally, disabled on CI
    val memoryMultiplier =
      if (customMemoryMultiplier.isPresent) {
        customMemoryMultiplier.get().toInt() / 100.0
      } else {
        pickValue(ci = 0.75, local = 0.5)
      }
    val coreMultiplier =
      if (customCoreMultiplier.isPresent) {
        customCoreMultiplier.get().toInt() / 100.0
      } else {
        pickValue(ci = 1.0, local = 0.5)
      }
    val hardware = SystemInfo().hardware
    val memoryGb = (hardware.memory.total / BYTES_PER_GB).roundToInt()
    val cores = hardware.processor.logicalProcessorCount
    diagnostic("Memory: ${memoryGb}GB")
    diagnostic("Cores: $cores")

    val rawXmx = floorInt(memoryGb * memoryMultiplier, minValue = 1)
    // CI uses a fixed-size heap
    val rawXms = pickValue(ci = rawXmx, local = floorInt(0.15 * memoryGb, minValue = 1))
    val gradleMemoryPercent =
      gradleMemoryPercentage.map { it.toFloat() / 100 }.getOrElse(DEFAULT_GRADLE_MEMORY_PERCENT)
    val kotlinMemoryPercent = (1 - gradleMemoryPercent)
    val gradleXmx: Int = floorInt(rawXmx * gradleMemoryPercent, minValue = minGradleXmx.get())
    val gradleXms: Int = floorInt(rawXms * gradleMemoryPercent, minValue = 1)
    val kotlinXmx: Int = floorInt(rawXmx * kotlinMemoryPercent, minValue = 1)
    val kotlinXms: Int = floorInt(rawXms * kotlinMemoryPercent, minValue = 1)
    val maxWorkers = floorInt(cores * coreMultiplier, minValue = 1)
    diagnostic("Gradle xms: ${gradleXms}GB")
    diagnostic("Gradle xmx: ${gradleXmx}GB")
    diagnostic("Kotlin Daemon xms: ${kotlinXms}GB")
    diagnostic("Kotlin Daemon xmx: ${kotlinXmx}GB")

    val gradleGcArgs = mutableListOf<String>()
    val kotlinDaemonGcArgs = mutableListOf<String>()
    val customGc = garbageCollector.orNull
    when {
      customGc != null && customGc != "default" -> {
        val args =
          listOf(
            "-XX:+$customGc",
            "-XX:+UnlockExperimentalVMOptions",
          )
        gradleGcArgs += args
        kotlinDaemonGcArgs += args
      }
      else -> {
        val simplePercent = (NEW_SIZE_PERCENT * 100).toInt()
        val args =
          listOf(
            "-XX:+UseG1GC",
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:G1NewSizePercent=$simplePercent",
            "-XX:G1MaxNewSizePercent=$simplePercent"
          )
        gradleGcArgs += args
        kotlinDaemonGcArgs += args
      }
    }

    kotlinDaemonGcArgs += jdkOpensAndExports

    // TODO Make CI use no GC on JDK 11+?
    // https://openjdk.java.net/jeps/318

    val jdkArgs = jdkOpensAndExports

    val extraArgs = gradleGcArgs.plus(jdkArgs).plus(extraJvmArgs.get()).joinToString(" ")

    val gradleDaemonArgs =
      GradleDaemonArgs(
        listOf(
          "-Xms${gradleXms}g",
          "-Xmx${gradleXmx}g",
          // Really important to set this for multiple reasons
          // - https://github.com/gradle/gradle/issues/19750
          // - Gradle's default is really low
          "-XX:MaxMetaspaceSize=1g",
        ) + extraArgs,
        maxWorkers
      )
    val kotlinDaemonArgs =
      KotlinDaemonArgs(listOf("-Xms${kotlinXms}g", "-Xmx${kotlinXmx}g") + kotlinDaemonGcArgs)

    return DaemonArgs(gradleDaemonArgs, kotlinDaemonArgs)
  }

  data class DaemonArgs(val gradle: GradleDaemonArgs, val kotlin: KotlinDaemonArgs)
  data class GradleDaemonArgs(val args: List<String>, val maxWorkers: Int)
  data class KotlinDaemonArgs(val args: List<String>)
}

private fun floorInt(a: Float, minValue: Int): Int = max(floor(a).toInt(), minValue)

private fun floorInt(a: Double, minValue: Int): Int = max(floor(a).toInt(), minValue)
