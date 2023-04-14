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
import kotlin.time.measureTimedValue
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option
import slack.gradle.SlackProperties
import slack.gradle.avoidance.AffectedProjectsDefaults.DEFAULT_INCLUDE_PATTERNS
import slack.gradle.avoidance.AffectedProjectsDefaults.DEFAULT_NEVER_SKIP_PATTERNS
import slack.gradle.setProperty
import slack.gradle.util.SgpLogger

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
  public val includePatterns: SetProperty<String> =
    project.objects.setProperty<String>().convention(DEFAULT_INCLUDE_PATTERNS)

  @get:Input
  public val excludePatterns: SetProperty<String> =
    project.objects.setProperty<String>().convention(emptySet())

  @get:Input
  public val neverSkipPatterns: SetProperty<String> =
    project.objects.setProperty<String>().convention(DEFAULT_NEVER_SKIP_PATTERNS)

  /**
   * A relative (to the repo root) path to a changed_files.txt that contains a newline-delimited
   * list of changed files. This is usually computed from a GitHub PR's changed files.
   */
  @get:Option(option = "changed-files", description = "A relative file path to changed_files.txt.")
  @get:Input
  public abstract val changedFiles: Property<String>

  /** Output diagnostics directory for use in debugging. */
  @get:OutputDirectory public abstract val diagnosticsDir: DirectoryProperty

  /** The output list of affected projects. */
  @get:OutputFile public abstract val outputFile: RegularFileProperty

  /** An output .focus file that could be used with the Focus plugin. */
  @get:OutputFile public abstract val outputFocusFile: RegularFileProperty

  /*
   * Internal properties.
   */

  /** Root repo directory. Used to compute relative paths and not considered an input. */
  @get:Internal internal abstract val rootDir: DirectoryProperty

  @get:Input internal abstract val dependencyGraph: Property<DependencyGraph.SerializableGraph>

  private lateinit var prefixLogger: SgpLogger

  init {
    group = "slack"
    description = "Computes affected projects and writes them to a file."
  }

  @TaskAction
  internal fun compute() {
    prefixLogger = SgpLogger.prefix(LOG, SgpLogger.gradle(logger))
    logTimedValue("gradle task computation") {
      // Clear outputs as needed
      outputFile.get().asFile.apply {
        if (exists()) {
          delete()
        }
      }
      outputFocusFile.get().asFile.apply {
        if (exists()) {
          delete()
        }
      }
      if (debug.get()) {
        diagnosticsDir.get().asFile.also { file ->
          if (file.exists()) {
            file.deleteRecursively()
          }
          file.mkdirs()
        }
      }
      val (affectedProjects, focusProjects) =
        AffectedProjectsComputer(
            rootDirPath = rootDir.asFile.get().toOkioPath(normalize = true),
            dependencyGraph = {
              logTimedValue("creating dependency graph") {
                DependencyGraph.create(dependencyGraph.get())
              }
            },
            includePatterns = includePatterns.get(),
            excludePatterns = excludePatterns.get(),
            neverSkipPatterns = neverSkipPatterns.get(),
            debug = debug.get(),
            diagnostics = this,
            changedFilePaths =
              logTimedValue("reading changed files") {
                val file = changedFiles.get()
                log("reading changed files from: $file")
                rootDir.file(file).get().asFile.readLines().map {
                  it.trim().toPath(normalize = true)
                }
              },
            logger = prefixLogger,
          )
          .compute()
          ?: return@logTimedValue

      // Generate affected_projects.txt
      log("writing affected projects to: $outputFile")
      outputFile.get().asFile.writeText(affectedProjects.sorted().joinToString("\n"))

      // Generate .focus settings file
      log("writing focus settings to: $outputFocusFile")
      outputFocusFile
        .get()
        .asFile
        .writeText(focusProjects.joinToString("\n") { "include(\"$it\")" })
    }
  }

  override fun write(name: String, content: () -> String) {
    if (debug.get()) {
      val file = diagnosticsDir.get().file("$name.txt").asFile
      log("writing diagnostic file: $path")
      file.parentFile.mkdirs()
      file.writeText(content())
    }
  }

  private fun log(message: String) {
    val withPrefix = "$LOG $message"
    // counter-intuitive to read but lifecycle is preferable when actively debugging, whereas
    // debug() only logs quietly unless --debug is used
    if (debug.get()) {
      logger.lifecycle(withPrefix)
    } else {
      logger.debug(withPrefix)
    }
  }

  private inline fun <T> logTimedValue(name: String, body: () -> T): T {
    val (value, duration) = measureTimedValue(body)
    log("$name took $duration")
    return value
  }

  internal companion object {
    private const val LOG = "[Skippy]"
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
      val configurationsToLook by lazy {
        val providedConfigs = slackProperties.affectedProjectConfigurations
        providedConfigs?.splitToSequence(',')?.toSet()?.let { providedConfigSet ->
          if (slackProperties.buildUponDefaultAffectedProjectConfigurations) {
            DEFAULT_CONFIGURATIONS + providedConfigSet
          } else {
            providedConfigSet
          }
        }
          ?: DEFAULT_CONFIGURATIONS
      }

      val moduleGraph by lazy {
        GradleDependencyGraphFactory.create(rootProject, configurationsToLook).serializableGraph()
      }

      return rootProject.tasks.register(
        "computeAffectedProjects",
        ComputeAffectedProjectsTask::class.java
      ) {
        debug.set(slackProperties.debug)
        rootDir.set(project.layout.projectDirectory)
        dependencyGraph.set(rootProject.provider { moduleGraph })
        diagnosticsDir.set(project.layout.buildDirectory.dir("skippy/diagnostics"))
        outputFile.set(project.layout.buildDirectory.file("skippy/affected_projects.txt"))
        outputFocusFile.set(project.layout.buildDirectory.file("skippy/focus.settings.gradle"))
        // Overrides of includes/neverSkippable patterns should be done in the consuming project
        // directly
      }
    }
  }
}
