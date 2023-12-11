/*
 * Copyright (C) 2023 Slack Technologies, LLC
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
package slack.gradle.avoidance

import com.jraska.module.graph.DependencyGraph
import com.jraska.module.graph.assertion.GradleDependencyGraphFactory
import java.nio.file.Path
import kotlin.io.path.deleteRecursively
import kotlin.io.path.readLines
import kotlin.io.path.writeLines
import kotlin.io.path.writeText
import kotlin.time.measureTimedValue
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option
import slack.gradle.SlackProperties
import slack.gradle.setProperty
import slack.gradle.util.SgpLogger
import slack.gradle.util.flatMapToSet
import slack.gradle.util.parallelMapNotNull
import slack.gradle.util.setDisallowChanges

/**
 * ### Usage
 * Example usage:
 * ```bash
 * ./gradlew computeAffectedProjects --changed-files changed_files.txt
 * ```
 *
 * @see AffectedProjectsComputer for most of the salient docs! The inputs in this task more or less
 *   match 1:1 to the properties of that class.
 */
@UntrackedTask(because = "This task is a meta task that more or less runs as a utility script.")
public abstract class ComputeAffectedProjectsTask : DefaultTask(), DiagnosticWriter {

  @get:Input
  public val debug: Property<Boolean> =
    project.objects.property(Boolean::class.java).convention(false)

  @get:Input
  public val mergeOutputs: Property<Boolean> =
    project.objects.property(Boolean::class.java).convention(true)

  @get:Input
  public val configs: NamedDomainObjectContainer<SkippyGradleConfig> =
    project.objects.domainObjectContainer(SkippyGradleConfig::class.java)

  @get:Input
  public val androidTestProjects: SetProperty<String> =
    project.objects.setProperty<String>().convention(emptySet())

  /**
   * A relative (to the repo root) path to a changed_files.txt that contains a newline-delimited
   * list of changed files. This is usually computed from a GitHub PR's changed files.
   */
  @get:Option(option = "changed-files", description = "A relative file path to changed_files.txt.")
  @get:Input
  public abstract val changedFiles: Property<String>

  /** Output diagnostics directory for use in debugging. */
  @get:OutputDirectory public abstract val diagnosticsDir: DirectoryProperty

  /** Output dir for skippy outputs. */
  @get:OutputDirectory public abstract val outputsDir: DirectoryProperty

  /*
   * Internal properties.
   */

  /** Root repo directory. Used to compute relative paths and not considered an input. */
  @get:Internal internal abstract val rootDir: DirectoryProperty

  @get:Input internal abstract val dependencyGraph: Property<DependencyGraph.SerializableGraph>

  init {
    group = "slack"
    description = "Computes affected projects and writes output files to an output directory."
  }

  @OptIn(DelicateCoroutinesApi::class)
  @TaskAction
  internal fun compute() {
    val configMap = configs.asMap

    // Extract the global config and apply it to each of the tools
    val configs =
      if (configMap.size == 1) {
        configMap.map { (tool, gradleConfig) -> gradleConfig.asSkippyConfig(tool) }
      } else {
        // No per-service configs, just use the global one
        val globalConfig =
          configMap.remove(SkippyExtension.GLOBAL_TOOL)?.asSkippyConfig(SkippyExtension.GLOBAL_TOOL)
            ?: error("No global config!")
        configMap
          .map { (tool, gradleConfig) -> gradleConfig.asSkippyConfig(tool) }
          .map { config -> config.overlayWith(globalConfig) }
      }

    val baseOutputDir = outputsDir.asFile.get().toPath()
    newFixedThreadPoolContext(configs.size, "computeAffectedProjects").use { dispatcher ->
      runBlocking {
        withContext(dispatcher) {
          val outputs =
            configs.parallelMapNotNull(configs.size) { config ->
              computeForTool(config, baseOutputDir)
            }
          if (mergeOutputs.get()) {
            val mergedOutput = WritableSkippyOutput("merged", baseOutputDir)
            val mergedAffectedProjects = async {
              outputs.flatMapToSet { it.affectedProjectsFile.readLines() }.toSortedSet()
            }
            val mergedAffectedAndroidTestProjects = async {
              outputs.flatMapToSet { it.affectedAndroidTestProjectsFile.readLines() }.toSortedSet()
            }
            val mergedFocusProjects = async {
              outputs.flatMapToSet { it.outputFocusFile.readLines() }.toSortedSet()
            }
            val (affectedProjects, affectedAndroidTestProjects, focusProjects) =
              listOf(
                  mergedAffectedProjects,
                  mergedAffectedAndroidTestProjects,
                  mergedFocusProjects,
                )
                .awaitAll()
            mergedOutput.affectedProjectsFile.writeLines(affectedProjects)
            mergedOutput.affectedAndroidTestProjectsFile.writeLines(affectedAndroidTestProjects)
            mergedOutput.outputFocusFile.writeLines(focusProjects)
          }
        }
      }
    }
  }

  /** Computes a [SkippyOutput] for the given [config]. */
  private fun computeForTool(config: SkippyConfig, outputDir: Path): SkippyOutput? {
    val tool = config.tool
    val skippyOutputs = WritableSkippyOutput(tool, outputDir)
    val prefixLogger = SgpLogger.prefix("$LOG_PREFIX[$tool]", SgpLogger.gradle(logger))
    return logTimedValue(tool, "gradle task computation") {
      if (debug.get()) {
        diagnosticsDir.get().asFile.also { file ->
          if (file.exists()) {
            file.deleteRecursively()
          }
          file.mkdirs()
        }
      }
      val (affectedProjects, focusProjects, affectedAndroidTestProjects) =
        AffectedProjectsComputer(
            rootDirPath = rootDir.asFile.get().toOkioPath(normalize = true),
            dependencyGraph = {
              logTimedValue(tool, "creating dependency graph") {
                DependencyGraph.create(dependencyGraph.get())
              }
            },
            config = config,
            androidTestProjects = androidTestProjects.get(),
            debug = debug.get(),
            diagnostics = this,
            changedFilePaths =
              logTimedValue(tool, "reading changed files") {
                val file = changedFiles.get()
                log(tool, "reading changed files from: $file")
                rootDir.file(file).get().asFile.readLines().map {
                  it.trim().toPath(normalize = true)
                }
              },
            logger = prefixLogger,
          )
          .compute() ?: return@logTimedValue null

      // Generate affected_projects.txt
      log(tool, "writing affected projects to: ${skippyOutputs.affectedProjectsFile}")
      skippyOutputs.affectedProjectsFile.writeText(affectedProjects.sorted().joinToString("\n"))

      // Generate affected_android_test_projects.txt
      log(
        tool,
        "writing affected androidTest projects to: ${skippyOutputs.affectedAndroidTestProjectsFile}"
      )
      skippyOutputs.affectedAndroidTestProjectsFile.writeText(
        affectedAndroidTestProjects.sorted().joinToString("\n")
      )

      // Generate .focus settings file
      log(tool, "writing focus settings to: ${skippyOutputs.outputFocusFile}")
      skippyOutputs.outputFocusFile.writeText(
        focusProjects.joinToString("\n") { "include(\"$it\")" }
      )

      skippyOutputs.delegate
    }
  }

  override fun write(tool: String, name: String, content: () -> String) {
    if (debug.get()) {
      val file = diagnosticsDir.get().file("$name.txt").asFile
      log(tool, "writing diagnostic file: $path")
      file.parentFile.mkdirs()
      file.writeText(content())
    }
  }

  private fun log(tool: String, message: String) {
    val withPrefix = "$LOG_PREFIX[$tool] $message"
    // counter-intuitive to read but lifecycle is preferable when actively debugging, whereas
    // debug() only logs quietly unless --debug is used
    if (debug.get()) {
      logger.lifecycle(withPrefix)
    } else {
      logger.debug(withPrefix)
    }
  }

  private inline fun <T> logTimedValue(tool: String, name: String, body: () -> T): T {
    val (value, duration) = measureTimedValue(body)
    log(tool, "$name took $duration")
    return value
  }

  internal companion object {
    internal const val NAME = "computeAffectedProjects"
    private const val LOG_PREFIX = "[Skippy]"
    private val DEFAULT_CONFIGURATIONS =
      setOf(
        "androidTestImplementation",
        "annotationProcessor",
        "api",
        "compileOnly",
        "debugApi",
        "debugImplementation",
        "implementation",
        "kapt",
        "kotlinCompilerPluginClasspath",
        "ksp",
        "releaseApi",
        "releaseImplementation",
        "testImplementation",
      )

    fun register(
      rootProject: Project,
      slackProperties: SlackProperties
    ): TaskProvider<ComputeAffectedProjectsTask> {
      val extension = rootProject.extensions.create("skippy", SkippyExtension::class.java)
      val configurationsToLook by lazy {
        val providedConfigs = slackProperties.affectedProjectConfigurations
        providedConfigs?.splitToSequence(',')?.toSet()?.let { providedConfigSet ->
          if (slackProperties.buildUponDefaultAffectedProjectConfigurations) {
            DEFAULT_CONFIGURATIONS + providedConfigSet
          } else {
            providedConfigSet
          }
        } ?: DEFAULT_CONFIGURATIONS
      }

      val moduleGraph by lazy {
        GradleDependencyGraphFactory.create(rootProject, configurationsToLook).serializableGraph()
      }

      return rootProject.tasks.register(NAME, ComputeAffectedProjectsTask::class.java) {
        debug.setDisallowChanges(slackProperties.debug)
        configs.addAll(extension.configs)
        rootDir.setDisallowChanges(project.layout.projectDirectory)
        dependencyGraph.setDisallowChanges(rootProject.provider { moduleGraph })
        diagnosticsDir.setDisallowChanges(project.layout.buildDirectory.dir("skippy/diagnostics"))
        outputsDir.setDisallowChanges(project.layout.buildDirectory.dir("skippy"))
        // Overrides of includes/neverSkippable patterns should be done in the consuming project
        // directly
      }
    }
  }
}
