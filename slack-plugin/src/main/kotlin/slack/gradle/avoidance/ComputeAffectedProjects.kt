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
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
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
internal abstract class ComputeAffectedProjects : DefaultTask() {

  /** Debugging flag. If enabled, extra diagnostics and logging is performed. */
  @get:Input abstract val debug: Property<Boolean>

  /**
   * A list of glob patterns for files to include in computing affected projects. This should
   * usually be source files, build files, gradle.properties files, and other projects that affect
   * builds.
   */
  @get:Input
  val includePatterns: ListProperty<String> =
    project.objects.listProperty(String::class.java).convention(DEFAULT_INCLUDE_PATTERNS)

  /**
   * A list of glob patterns that, if matched with a file, indicate that nothing should be skipped
   * and no [outputFile] or [outputFocusFile] will be generated.
   *
   * This is useful for globally-affecting things like root build files, `libs.versions.toml`, etc.
   */
  @get:Input
  val neverSkipPatterns: ListProperty<String> =
    project.objects.listProperty(String::class.java).convention(DEFAULT_NEVER_SKIP_PATTERNS)

  /**
   * A relative (to the repo root) path to a changed_files.txt that contains a newline-delimited
   * list of changed files. This is usually computed from a GitHub PR's changed files.
   */
  @get:Option(option = "changed-files", description = "A relative file path to changed_files.txt.")
  @get:Input
  abstract val changedFiles: Property<String>

  /** Output diagnostics directory for use in debugging. */
  @get:OutputDirectory abstract val diagnosticsDir: DirectoryProperty

  /** The output list of affected projects. */
  @get:OutputFile abstract val outputFile: RegularFileProperty

  /** An output .focus file that could be used with the Focus plugin. */
  @get:OutputFile abstract val outputFocusFile: RegularFileProperty

  /*
   * Internal properties.
   */

  /** Root repo directory. Used to compute relative paths and not considered an input. */
  @get:Internal internal abstract val rootDir: DirectoryProperty

  /** The serialized dependency graph as computed from our known configurations. */
  @get:Input internal abstract val dependencyGraph: Property<DependencyGraph.SerializableGraph>

  init {
    group = "slack"
    description = "Computes affected projects and writes them to a file."
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

  @TaskAction
  fun compute() {
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
      diagnosticsDir.asFile.get().apply {
        if (exists()) {
          deleteRecursively()
        }
        mkdirs()
      }
    }
    logTimedValue("full computation") { computeImpl() }
  }

  private fun computeImpl() {
    val fs = FileSystems.getDefault()

    log("reading changed files from: ${changedFiles.get()}")
    val changedFilesPaths =
      logTimedValue("reading changed files") {
        rootDir.file(changedFiles).get().asFile.readLines().map { Paths.get(it.trim()) }
      }
    log("changedFilesPaths: $changedFilesPaths")

    log("includePatterns: ${includePatterns.get()}")
    val filteredChangedFilePaths =
      logTimedValue("filtering changed files") {
        changedFilesPaths.filter {
          includePatterns.get().any { pattern -> fs.getPathMatcher("glob:$pattern").matches(it) }
        }
      }
    log("filteredChangedFilePaths: $filteredChangedFilePaths")

    val rootDirPath = rootDir.get().asFile.toPath()

    val pathMatchers =
      logTimedValue("creating path matchers") {
        neverSkipPatterns.get().map { fs.getPathMatcher("glob:$it") }
      }
    log("neverSkipPatterns: ${neverSkipPatterns.get()}")

    // TODO this is slow as it checks all of them, but in the future we could hide this behind a
    //  debug flag
    val pathsWithSkippability =
      logTimedValue("checking for non-skippable files") {
        filteredChangedFilePaths.associateWith { path -> pathMatchers.find { it.matches(path) } }
      }
    if (pathsWithSkippability.values.any { it != null }) {
      // No file means we run everything.
      logger.lifecycle(
        "$LOG Never-skip pattern(s) matched: ${pathsWithSkippability.filterValues { it != null }}."
      )
      return
    }

    val nearestProjectCache = mutableMapOf<Path, Path>()

    // Mapping of Gradle project paths (like ":app") to the ChangedProject representation.
    val changedProjects =
      logTimedValue("computing changed projects") {
        filteredChangedFilePaths
          .groupBy { it.findNearestProjectDir(rootDirPath, nearestProjectCache) }
          .entries
          .associate { (projectPath, files) ->
            val gradlePath = ":${projectPath.toString().replace(File.separatorChar, ':')}"
            gradlePath to ChangedProject(projectPath, gradlePath, files.toSet())
          }
      }
    writeDiagnostic("changedProjects.txt") {
      changedProjects.entries
        .sortedBy { it.key }
        .joinToString("\n") { (_, v) ->
          val testOnlyString = if (v.onlyTestsAreChanged) " (tests only)" else ""
          val lintBaselineOnly = if (v.onlyLintBaselineChanged) " (lint-baseline.xml only)" else ""
          "${v.gradlePath}$testOnlyString$lintBaselineOnly\n${v.changedPaths.sorted().joinToString("\n") { "-- $it" } }"
        }
    }

    val graph = logTimedValue("creating graph") { DependencyGraph.create(dependencyGraph.get()) }

    val projectsToDependencies: Map<String, Set<String>> =
      logTimedValue("computing dependencies") {
        graph.nodes().associate { node ->
          val dependencies = mutableSetOf<DependencyGraph.Node>()
          node.visitDependencies(dependencies)
          node.key to dependencies.mapTo(mutableSetOf()) { it.key }
        }
      }

    writeDiagnostic("projectsToDependencies.txt") {
      buildString {
        for ((project, dependencies) in projectsToDependencies.toSortedMap()) {
          appendLine(project)
          for (dep in dependencies.sorted()) {
            appendLine("-> $dep")
          }
        }
      }
    }

    val projectsToDependents = logTimedValue("computing dependents", projectsToDependencies::flip)

    writeDiagnostic("projectsToDependents.txt") {
      buildString {
        for ((project, dependencies) in projectsToDependents.toSortedMap()) {
          appendLine(project)
          for (dep in dependencies.sorted()) {
            appendLine("-> $dep")
          }
        }
      }
    }

    val allAffectedProjects = buildSet {
      for ((path, change) in changedProjects) {
        add(path)
        if (change.affectsDependents) {
          addAll(projectsToDependents[path] ?: emptySet())
        }
      }
    }

    logger.lifecycle("$LOG Found ${allAffectedProjects.size} affected projects.")

    // Generate affected_projects.txt
    log("writing affected projects to: ${outputFile.get()}")
    outputFile.get().asFile.writeText(allAffectedProjects.sorted().joinToString("\n"))

    // Generate .focus settings file
    val allRequiredProjects =
      allAffectedProjects
        .flatMapTo(mutableSetOf()) { project ->
          val dependencies = projectsToDependencies[project].orEmpty()
          dependencies + project
        }
        .sorted()
    log("writing focus settings to: ${outputFocusFile.get()}")
    outputFocusFile
      .get()
      .asFile
      .writeText(allRequiredProjects.joinToString("\n") { "include(\"$it\")" })
  }

  private fun writeDiagnostic(fileName: String, content: () -> String) {
    if (debug.get()) {
      val file = diagnosticsDir.file(fileName).get().asFile
      file.parentFile.mkdirs()
      file.createNewFile()
      file.writeText(content())
    }
  }

  companion object {
    private const val LOG = "[Skippy]"

    // TODO what about paparazzi snapshots, lint baselines
    private val DEFAULT_INCLUDE_PATTERNS =
      listOf(
        "**/*.kt",
        "*.gradle.kts",
        "**/*.gradle.kts",
        "**/*.java",
        "**/*.xml",
        "**/gradle.properties",
      )

    private val DEFAULT_NEVER_SKIP_PATTERNS =
      listOf(
        // root build.gradle.kts and settings.gradle.kts files
        "*.gradle.kts",
        // root gradle.properties file
        "gradle.properties",
        "**/*.versions.toml",
        "ci/**",
        ".buildkite/**",
      )

    fun register(
      rootProject: Project,
      slackProperties: SlackProperties
    ): TaskProvider<ComputeAffectedProjects> {
      // TODO any others?
      // TODO what about testFixtures?
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
        ComputeAffectedProjects::class.java
      ) {
        debug.set(slackProperties.debug)
        rootDir.set(project.layout.projectDirectory)
        dependencyGraph.set(rootProject.provider { moduleGraph })
        diagnosticsDir.set(project.layout.buildDirectory.dir("skippy/diagnostics"))
        outputFile.set(project.layout.buildDirectory.file("skippy/affected_projects.txt"))
        outputFocusFile.set(project.layout.buildDirectory.file("skippy/focus.settings.gradle"))
        // TODO neverSkipPatterns
        // TODO includePatterns
      }
    }
  }

  private inline fun <T> logTimedValue(name: String, body: () -> T): T {
    val (value, duration) = measureTimedValue(body)
    log("$name took $duration")
    return value
  }
}

/**
 * Given a file path like
 * `/Users/username/projects/MyApp/app/src/main/kotlin/com/example/myapp/MainActivity.kt`, returns
 * the nearest Gradle project [Path] like `/Users/username/projects/MyApp/app`.
 */
private fun Path.findNearestProjectDir(repoRoot: Path, cache: MutableMap<Path, Path>): Path {
  val currentDir =
    when {
      isRegularFile() -> parent
      isDirectory() -> this
      // TODO deleted file. Temporarily make it and try again?
      else -> error("Unsupported file type: $this")
    }
  return findNearestProjectDirRecursive(repoRoot, currentDir, cache)
}

private fun findNearestProjectDirRecursive(
  repoRoot: Path,
  currentDir: Path?,
  cache: MutableMap<Path, Path>
): Path {
  if (currentDir == null || currentDir == repoRoot) {
    error("Could not find build.gradle(.kts) for $currentDir")
  }

  return cache.getOrPut(currentDir) {
    val hasBuildFile =
      currentDir.resolve("build.gradle.kts").exists() || currentDir.resolve("build.gradle").exists()
    if (hasBuildFile) {
      return currentDir
    }
    findNearestProjectDirRecursive(repoRoot, currentDir.parent, cache)
  }
}

private fun DependencyGraph.Node.visitDependencies(setToAddTo: MutableSet<DependencyGraph.Node>) {
  for (dependency in dependsOn) {
    if (!setToAddTo.add(dependency)) {
      // Only add transitives if we haven't already seen this dependency.
      dependency.visitDependencies(setToAddTo)
    }
  }
}

/**
 * Flips a map. In the context of [ComputeAffectedProjects], we use this to flip a map of projects
 * to their dependencies to a map of projects to the projects that depend on them. We use this to
 * find all affected projects given a seed of changed projects.
 *
 * Example:
 *
 *  ```
 *  Given a map
 *  {a:[b, c], b:[d], c:[d], d:[]}
 *  return
 *  {b:[a], c:[a], d:[b, c]}
 *  ```
 */
private fun Map<String, Set<String>>.flip(): Map<String, Set<String>> {
  val flipped = mutableMapOf<String, MutableSet<String>>()
  for ((project, dependenciesSet) in this) {
    for (dependency in dependenciesSet) {
      flipped.getOrPut(dependency, ::mutableSetOf).add(project)
    }
  }
  return flipped
}

/**
 * Represents a changed project as computed by [ComputeAffectedProjects.changedFiles].
 *
 * @property path The [Path] to the project directory.
 * @property gradlePath The Gradle project path (e.g. `:app`).
 * @property changedPaths The set of [Paths][Path] to files that have changed.
 */
private data class ChangedProject(
  val path: Path,
  val gradlePath: String,
  val changedPaths: Set<Path>,
) {
  /**
   * Returns true if all changed files are in a test directory and therefore do not carry-over to
   * downstream dependents.
   */
  val testPaths: Set<Path> = changedPaths.filterTo(mutableSetOf(), testPathMatcher::matches)
  val onlyTestsAreChanged = testPaths.size == changedPaths.size

  /**
   * Returns true if all changed files are just `lint-baseline.xml`. This is useful because it means
   * they don't affect downstream dependants.
   */
  val lintBaseline: Set<Path> =
    changedPaths.filterTo(mutableSetOf(), lintBaselinePathMatcher::matches)
  val onlyLintBaselineChanged = lintBaseline.size == changedPaths.size

  /**
   * Remaining affected files that aren't test paths or lint baseline, and therefore affect
   * dependants.
   */
  val dependantAffectingFiles = changedPaths - testPaths - lintBaseline

  /** Shorthand for if [dependantAffectingFiles] is not empty. */
  val affectsDependents = dependantAffectingFiles.isNotEmpty()

  companion object {
    // This covers snapshot tests too as they are under src/test/snapshots/**
    private val testPathMatcher =
      FileSystems.getDefault().getPathMatcher("glob:**/src/*{test,androidTest}/**")
    private val lintBaselinePathMatcher =
      FileSystems.getDefault().getPathMatcher("glob:**/lint-baseline.xml")
  }
}
