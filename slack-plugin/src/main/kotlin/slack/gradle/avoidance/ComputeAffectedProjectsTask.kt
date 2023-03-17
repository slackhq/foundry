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
import java.nio.file.Paths
import kotlin.time.measureTimedValue
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option
import slack.gradle.SlackProperties
import slack.gradle.util.SgpLogger

/**
 * This task is a meta task to compute the set of projects that are affected by a set of changed
 * files ([changedFiles]).
 *
 * The intention of this task is to run as a preflight step in pull requests to only run a subset of
 * checks affected by files changed _in_ that PR. This allows CI on PRs to safely complete faster
 * and reduce CI usage.
 *
 * ### Inputs
 *
 * The primary input is [changedFiles], which is a newline-delimited list of files that have
 * changed. Usually the files changed in a pull request. This can be specified via the
 * `--changed-files` CLI option, and its path is resolved against the root project directory.
 *
 * [includePatterns] is a list of glob patterns that are used to filter the list of changed files.
 * These should usually be source files that are deemed to participate in builds (e.g. `.kt` files).
 *
 * [neverSkipPatterns] is a list of glob patterns that, if matched with any changed file, indicate
 * that nothing should be skipped and the full build should run. This is important for files like
 * version catalog toml files or root build.gradle file changes.
 *
 * ### Outputs
 *
 * The primary output [outputFile] is a newline-delimited list of Gradle project paths that are
 * determined to be affected. This is intended to be used as an input to a subsequent Gradle
 * invocation to inform which projects can be avoided.
 *
 * A secondary output file is [outputFocusFile]. This is a `focus.settings.gradle` file that can be
 * used with the dropbox/focus plugin, and will be a minimal list of projects needed to build the
 * affected projects.
 *
 * With both outputs, if any "never-skippable" files [neverSkipPatterns] are changed, then no output
 * file is produced and all projects are considered affected. If a file is produced but has no
 * content written to it, that simply means that no projects are affected.
 *
 * ### Debugging
 *
 * To debug this task, pass in `-Pslack.debug=true` to enable debug logging. This will also output
 * verbose diagnostics to [diagnosticsDir] (usually `build/skippy/diagnostics`). Debug mode will
 * also output timings.
 *
 * ### Usage
 * Example usage:
 * ```bash
 * ./gradlew computeAffectedProjects --changed-files changed_files.txt
 * ```
 */
@UntrackedTask(because = "This task is a meta task that more or less runs as a utility script.")
public abstract class ComputeAffectedProjectsTask : DefaultTask(), DiagnosticWriter {

  /** Debugging flag. If enabled, extra diagnostics and logging is performed. */
  @get:Input
  public val debug: Property<Boolean> =
    project.objects.property(Boolean::class.java).convention(false)

  /**
   * A list of glob patterns for files to include in computing affected projects. This should
   * usually be source files, build files, gradle.properties files, and other projects that affect
   * builds.
   */
  @get:Input
  public val includePatterns: ListProperty<String> =
    project.objects
      .listProperty(String::class.java)
      .convention(AffectedProjectsComputer.DEFAULT_INCLUDE_PATTERNS)

  /**
   * A list of glob patterns that, if matched with a file, indicate that nothing should be skipped
   * and no [outputFile] or [outputFocusFile] will be generated.
   *
   * This is useful for globally-affecting things like root build files, `libs.versions.toml`, etc.
   */
  @get:Input
  public val neverSkipPatterns: ListProperty<String> =
    project.objects
      .listProperty(String::class.java)
      .convention(AffectedProjectsComputer.DEFAULT_NEVER_SKIP_PATTERNS)

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

  /** The serialized dependency graph as computed from our known configurations. */
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
            rootDirPath = rootDir.asFile.get().toPath(),
            dependencyGraph = {
              logTimedValue("creating dependency graph") {
                DependencyGraph.create(dependencyGraph.get())
              }
            },
            includePatterns = includePatterns.get(),
            neverSkipPatterns = neverSkipPatterns.get(),
            debug = debug.get(),
            diagnostics = this,
            changedFilePaths =
              logTimedValue("reading changed files") {
                val file = changedFiles.get()
                log("reading changed files from: $file")
                rootDir.file(file).get().asFile.readLines().map { Paths.get(it.trim()) }
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

    fun register(
      rootProject: Project,
      slackProperties: SlackProperties
    ): TaskProvider<ComputeAffectedProjectsTask> {
      // TODO any others?
      val configurationsToLook: Set<String> =
        setOf(
          "api",
          "implementation",
          "ksp",
          "testImplementation",
          "androidTestImplementation",
          "compileOnly",
          "annotationProcessor",
        )

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
