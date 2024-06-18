package slack.gradle.kgp

import com.android.build.gradle.BaseExtension
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask
import org.jetbrains.kotlin.gradle.plugin.KaptExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin
import slack.gradle.Configurations
import slack.gradle.Configurations.isKnownConfiguration
import slack.gradle.SlackProperties
import slack.gradle.SlackTools
import slack.gradle.configure
import slack.gradle.dependencies.BuildConfig
import slack.gradle.lint.DetektTasks
import slack.gradle.util.configureKotlinCompilationTask

/** Common configuration for Kotlin projects. */
internal object KgpTasks {

  private val KOTLIN_COMPILER_ARGS =
    mutableListOf<String>()
      .apply {
        addAll(BuildConfig.KOTLIN_COMPILER_ARGS)
        // Left as a toe-hold for any future dynamic arguments
      }
      .distinct()

  @Suppress("LongMethod")
  fun configure(
    project: Project,
    jdkVersion: Int?,
    jvmTargetVersion: Int,
    slackTools: SlackTools,
    slackProperties: SlackProperties,
  ) {
    val actualJvmTarget =
      if (jvmTargetVersion == 8) {
        "1.8"
      } else {
        jvmTargetVersion.toString()
      }

    val detektConfigured = AtomicBoolean()
    // Must be outside the withType() block below because you can't apply new plugins in that block
    if (slackProperties.autoApplyDetekt) {
      project.project.pluginManager.apply("io.gitlab.arturbosch.detekt")
    }

    project.plugins.withType(KotlinBasePlugin::class.java).configureEach {
      val kotlinExtension = project.project.kotlinExtension
      kotlinExtension.apply {
        kotlinDaemonJvmArgs = slackTools.globalConfig.kotlinDaemonArgs
        if (jdkVersion != null) {
          jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(jdkVersion))
            slackTools.globalConfig.jvmVendor?.let(vendor::set)
          }
        }
      }

      val isKotlinAndroid = kotlinExtension is KotlinAndroidProjectExtension

      project.tasks.configureKotlinCompilationTask(includeKaptGenerateStubsTask = true) {
        // Don't add compiler args to KaptGenerateStubsTask because it inherits arguments from the
        // target compilation
        val isKaptGenerateStubsTask = this is KaptGenerateStubsTask

        compilerOptions {
          progressiveMode.set(true)
          // TODO probably just want to make these configurable in SlackProperties
          optIn.addAll(
            "kotlin.contracts.ExperimentalContracts",
            "kotlin.experimental.ExperimentalTypeInference",
            "kotlin.ExperimentalStdlibApi",
            "kotlin.time.ExperimentalTime",
          )
          if (
            !slackProperties.allowWarnings &&
              !this@configureKotlinCompilationTask.name.contains("test", ignoreCase = true)
          ) {
            allWarningsAsErrors.set(true)
          }
          if (!isKaptGenerateStubsTask) {
            freeCompilerArgs.addAll(KOTLIN_COMPILER_ARGS)
          }

          if (this is KotlinJvmCompilerOptions) {
            jvmTarget.set(JvmTarget.fromTarget(actualJvmTarget))
            // Potentially useful for static analysis or annotation processors
            javaParameters.set(true)
            freeCompilerArgs.addAll(BuildConfig.KOTLIN_JVM_COMPILER_ARGS)

            // Set the module name to a dashified version of the project path to ensure uniqueness
            // in created .kotlin_module files
            moduleName.set(project.path.replace(":", "-"))

            if (!isKotlinAndroid) {
              // https://jakewharton.com/kotlins-jdk-release-compatibility-flag/
              freeCompilerArgs.add("-Xjdk-release=$actualJvmTarget")
            }
          }
        }
      }

      project.configureFreeKotlinCompilerArgs()

      if (!detektConfigured.getAndSet(true)) {
        DetektTasks.configureSubProject(
          project.project,
          slackProperties,
          slackTools.globalConfig.affectedProjects,
          actualJvmTarget,
        )
      }
    }

    project.pluginManager.withPlugin("org.jetbrains.kotlin.android") {
      // Configure kotlin sources in Android projects
      project.configure<BaseExtension> {
        this.sourceSets.configureEach {
          val nestedSourceDir = "src/${this.name}/kotlin"
          val dir = File(project.projectDir, nestedSourceDir)
          if (dir.exists()) {
            // Standard source set
            // Only added if it exists to avoid potentially adding empty source dirs
            this.java.srcDirs(project.layout.projectDirectory.dir(nestedSourceDir))
          }
        }
      }
    }

    project.pluginManager.withPlugin("org.jetbrains.kotlin.android.extensions") {
      throw GradleException(
        "Don't use the deprecated 'android.extensions' plugin, switch to " +
          "'plugin.parcelize' instead."
      )
    }

    project.pluginManager.withPlugin("org.jetbrains.kotlin.kapt") {
      project.configure<KaptExtension> {
        // By default, Kapt replaces unknown types with `NonExistentClass`. This flag asks kapt
        // to infer the type, which is useful for processors that reference to-be-generated
        // classes.
        // https://kotlinlang.org/docs/reference/kapt.html#non-existent-type-correction
        this.correctErrorTypes = true

        // Maps source errors to Kotlin sources rather than Java stubs
        // Disabled because this triggers a bug in kapt on android 30
        // https://github.com/JetBrains/kotlin/pull/3610
        this.mapDiagnosticLocations = false
      }

      // See doc on the property for details
      if (!slackProperties.enableKaptInTests) {
        project.tasks.configureEach {
          if (name.startsWith("kapt") && name.endsWith("TestKotlin", ignoreCase = true)) {
            enabled = false
          }
        }
      }
    }
  }

  /**
   * Configures per-dependency free Kotlin compiler args. This is necessary because otherwise
   * kotlinc will emit angry warnings.
   */
  private fun Project.configureFreeKotlinCompilerArgs() {
    logger.debug("Configuring specific Kotlin compiler args on $path")
    val once = AtomicBoolean()
    configurations.configureEach {
      if (isKnownConfiguration(name, Configurations.Groups.RUNTIME)) {
        incoming.afterResolve {
          dependencies.forEach { dependency ->
            KotlinArgConfigs.ALL[dependency.name]?.let { config ->
              if (once.compareAndSet(false, true)) {
                tasks.configureKotlinCompilationTask {
                  compilerOptions { freeCompilerArgs.addAll(config.args) }
                }
              }
            }
          }
        }
      }
    }
  }

  interface KotlinArgConfig {
    val targetDependency: String
    val args: Set<String>
  }

  @Suppress("unused") // Nested classes here are looked up reflectively
  object KotlinArgConfigs {

    val ALL: Map<String, KotlinArgConfig> by lazy {
      KotlinArgConfigs::class
        .nestedClasses
        .map { it.objectInstance }
        .filterIsInstance<KotlinArgConfig>()
        .associateBy { it.targetDependency }
    }

    object Coroutines : KotlinArgConfig {
      override val targetDependency: String = "kotlinx-coroutines-core"
      override val args =
        setOf(
          "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
          "-opt-in=kotlinx.coroutines.FlowPreview",
        )
    }
  }
}
