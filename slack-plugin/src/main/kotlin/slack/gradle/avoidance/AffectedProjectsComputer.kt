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
import java.util.Collections.unmodifiableSet
import kotlin.time.measureTimedValue
import okio.FileSystem
import okio.Path
import slack.gradle.util.SgpLogger

/**
 * This is a program compute the set of Gradle projects that are affected by a set of changed files
 * ([changedFilePaths]).
 *
 * The intention of this program is to run as a preflight step in pull requests to only run a subset
 * of checks affected by files changed _in_ that PR. This allows CI on PRs to safely complete faster
 * and reduce CI usage.
 *
 * **NOTE**: Included builds are not supported.
 *
 * ### Inputs
 *
 * The primary input is [changedFilePaths], which is a newline-delimited list of files that have
 * changed. Usually the files changed in a pull request.
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
 * The primary output [AffectedProjectsResult.affectedProjects] is a set of Gradle project paths
 * that are determined to be affected. This is intended to be used as an input to a subsequent
 * Gradle invocation (usually as a file) to inform which projects can be avoided.
 *
 * A secondary output file is [AffectedProjectsResult.focusProjects]. This is a set of Gradle
 * project paths that can be written to a `focus.settings.gradle` file that can be used with the
 * dropbox/focus plugin, and will be a minimal list of projects needed to build the affected
 * projects.
 *
 * With both outputs, if any "never-skippable" files [neverSkipPatterns] are changed, then no output
 * file is produced and all projects are considered affected. If a file is produced but has no
 * content written to it, that simply means that no projects are affected.
 *
 * ### Debugging
 *
 * To debug this task, pass in `-Pslack.debug=true` to enable debug logging. This will also output
 * verbose diagnostics to [diagnostics] (usually `build/skippy/diagnostics`). Debug mode will also
 * output timings.
 *
 * @property rootDirPath Root repo directory. Used to compute relative paths and not considered an
 *   input.
 * @property dependencyGraph The serialized dependency graph as computed from our known
 *   configurations. Lazily loaded and only invoked onces.
 * @property changedFilePaths A relative (to the repo root) path to a changed_files.txt that
 *   contains a newline-delimited list of changed files. This is usually computed from a GitHub PR's
 *   changed files.
 * @property includePatterns A set of glob patterns for files to include in computing affected
 *   projects. This should usually be source files, build files, gradle.properties files, and other
 *   projects that affect builds.
 * @property excludePatterns A set of glob patterns for files to exclude from computing affected
 *   projects. This is run _after_ [includePatterns] and can be useful for excluding files that
 *   would otherwise be included by an existing inclusion pattern.
 * @property neverSkipPatterns A set of glob patterns that, if matched with a file, indicate that
 *   nothing should be skipped and [compute] will return null. This is useful for globally-affecting
 *   things like root build files, `libs.versions.toml`, etc. **NOTE**: This list is always merged
 *   with [includePatterns] as these are implicitly relevant files.
 * @property debug Debugging flag. If enabled, extra diagnostics and logging is performed.
 * @property logger A logger to use for logging.
 */
internal class AffectedProjectsComputer(
  private val rootDirPath: Path,
  private val dependencyGraph: () -> DependencyGraph,
  private val changedFilePaths: List<Path>,
  private val diagnostics: DiagnosticWriter = DiagnosticWriter.NoOp,
  private val includePatterns: Set<String> = DEFAULT_INCLUDE_PATTERNS,
  private val excludePatterns: Set<String> = emptySet(),
  private val neverSkipPatterns: Set<String> = DEFAULT_NEVER_SKIP_PATTERNS,
  private val debug: Boolean = false,
  private val fileSystem: FileSystem = FileSystem.SYSTEM,
  private val logger: SgpLogger = SgpLogger.noop()
) {
  fun compute(): AffectedProjectsResult? {
    return logTimedValue("full computation") { computeImpl() }
  }

  private fun computeImpl(): AffectedProjectsResult? {
    log("root dir path is: $rootDirPath")
    check(rootDirPath.exists()) { "Root dir path $rootDirPath does not exist" }
    log("changedFilePaths: $changedFilePaths")

    log("includePatterns: $includePatterns")
    // Merge include patterns with never-skip patterns. This is because
    val mergedIncludePatterns = (includePatterns + neverSkipPatterns).toSet()
    log("mergedIncludePatterns: $mergedIncludePatterns")

    val includedChangedFilePaths =
      logTimedValue("filtering changed files with includes") {
        filterIncludes(changedFilePaths, mergedIncludePatterns)
      }
    log("includedChangedFilePaths: $includedChangedFilePaths")

    val filteredChangedFilePaths =
      logTimedValue("filtering changed files with excludes") {
        filterExcludes(includedChangedFilePaths, excludePatterns)
      }
    log("filteredChangedFilePaths: $filteredChangedFilePaths")

    val neverSkipPathMatchers =
      logTimedValue("creating path matchers") { neverSkipPatterns.map(String::toPathMatcher) }
    log("neverSkipPatterns: $neverSkipPatterns")

    if (debug) {
      // Do a slower, more verbose check in debug
      val pathsWithSkippability =
        logTimedValue("checking for non-skippable files") {
          anyNeverSkipDebug(filteredChangedFilePaths, neverSkipPathMatchers)
        }
      if (pathsWithSkippability.values.any { it != null }) {
        // Produce no outputs, run everything
        logger.lifecycle(
          "Never-skip pattern(s) matched: ${pathsWithSkippability.filterValues { it != null }}."
        )
        return null
      }
    } else {
      // Do a fast check for never-skip paths when not debugging
      if (anyNeverSkip(filteredChangedFilePaths, neverSkipPathMatchers)) {
        // Produce no outputs, run everything
        logger.lifecycle("Never-skip pattern(s) matched.")
        return null
      }
    }

    val nearestProjectCache = mutableMapOf<Path, Path?>()

    // Mapping of Gradle project paths (like ":app") to the ChangedProject representation.
    val changedProjects =
      logTimedValue("computing changed projects") {
        filteredChangedFilePaths
          .groupBy {
            // We need the full path here in order to resolve file attributes correctly
            rootDirPath.resolve(it).findNearestProjectDir(rootDirPath, nearestProjectCache)
          }
          .filterNotNullKeys()
          .entries
          .associate { (projectPath, files) ->
            // Get the relative path back now, we don't need the fully qualified path anymore
            val relativePath = projectPath.relativeTo(rootDirPath)
            val gradlePath = ":${relativePath.toString().replace(Path.DIRECTORY_SEPARATOR, ":")}"
            gradlePath to ChangedProject(relativePath, gradlePath, files.toSet())
          }
      }
    diagnostics.write("changedProjects.txt") {
      changedProjects.entries
        .sortedBy { it.key }
        .joinToString("\n") { (_, v) ->
          val testOnlyString = if (v.onlyTestsAreChanged) " (tests only)" else ""
          val lintBaselineOnly = if (v.onlyLintBaselineChanged) " (lint-baseline.xml only)" else ""
          "${v.gradlePath}$testOnlyString$lintBaselineOnly\n${v.changedPaths.sorted().joinToString("\n") { "-- $it" } }"
        }
    }

    val projectsToDependencies: Map<String, Set<String>> =
      logTimedValue("computing dependencies") {
        dependencyGraph().nodes().associate { node ->
          val dependencies = mutableSetOf<DependencyGraph.Node>()
          node.visitDependencies(dependencies)
          node.key to dependencies.mapTo(mutableSetOf()) { it.key }
        }
      }

    diagnostics.write("projectsToDependencies.txt") {
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

    diagnostics.write("projectsToDependents.txt") {
      buildString {
        for ((project, dependencies) in projectsToDependents.toSortedMap()) {
          appendLine(project)
          for (dep in dependencies.sorted()) {
            appendLine("-> $dep")
          }
        }
      }
    }

    val allAffectedProjects =
      buildSet {
          for ((path, change) in changedProjects) {
            add(path)
            if (change.affectsDependents) {
              addAll(projectsToDependents[path] ?: emptySet())
            }
          }
        }
        .toSortedSet()

    logger.lifecycle("Found ${allAffectedProjects.size} affected projects.")

    val allRequiredProjects =
      allAffectedProjects
        .flatMapTo(mutableSetOf()) { project ->
          val dependencies = projectsToDependencies[project].orEmpty()
          dependencies + project
        }
        .toSortedSet()
    return AffectedProjectsResult(allAffectedProjects, allRequiredProjects)
  }

  private fun log(message: String) {
    // counter-intuitive to read but lifecycle is preferable when actively debugging, whereas
    // debug() only logs quietly unless --debug is used
    if (debug) {
      logger.lifecycle(message)
    } else {
      logger.debug(message)
    }
  }

  private inline fun <T> logTimedValue(name: String, body: () -> T): T {
    val (value, duration) = measureTimedValue(body)
    log("$name took $duration")
    return value
  }

  /**
   * Given a file path like
   * `/Users/username/projects/MyApp/app/src/main/kotlin/com/example/myapp/MainActivity.kt`, returns
   * the nearest Gradle project [Path] like `/Users/username/projects/MyApp/app`.
   */
  private fun Path.findNearestProjectDir(
    repoRoot: Path,
    cache: MutableMap<Path, Path?>,
  ): Path? {
    val currentDir =
      when {
        !exists() -> {
          /*
           * Deleted file. Still check the parent dirs though because if the project itself still
           * exists, it still affects downstream
           *
           * There _is_ an edge case here though: what if the intermediary project was deleted but
           * nested below another, real project? How do we know when to stop?
           * Well, we're protected from this scenario by the fact that such a change would incur a change to
           * `settings.gradle.kts`, and subsequently all projects would be deemed affected.
           */
          parent
        }
        isRegularFile() -> parent
        isDirectory() -> this
        else -> error("Unsupported file type: $this")
      }
    return findNearestProjectDirRecursive(repoRoot, this, currentDir, cache)
  }

  private fun Path.isRegularFile(): Boolean = fileSystem.metadataOrNull(this)?.isRegularFile == true

  private fun Path.isDirectory(): Boolean = fileSystem.metadataOrNull(this)?.isDirectory == true

  private fun Path.exists(): Boolean = fileSystem.exists(this)

  private fun findNearestProjectDirRecursive(
    repoRoot: Path,
    originalPath: Path,
    currentDir: Path?,
    cache: MutableMap<Path, Path?>
  ): Path? {
    if (currentDir == null || currentDir == repoRoot) {
      error("Could not find build.gradle(.kts) for $originalPath")
    }

    return cache.getOrPut(currentDir) {
      // Note the dir may not exist, but that's ok because we still want to check its parents
      val hasBuildFile =
        currentDir.resolve("build.gradle.kts").exists() ||
          currentDir.resolve("build.gradle").exists()
      if (hasBuildFile) {
        return currentDir
      }
      findNearestProjectDirRecursive(repoRoot, originalPath, currentDir.parent, cache)
    }
  }

  companion object {
    val DEFAULT_INCLUDE_PATTERNS: Set<String> =
      unmodifiableSet(
        setOf(
          "**/*.kt",
          "*.gradle",
          "**/*.gradle",
          "*.gradle.kts",
          "**/*.gradle.kts",
          "**/*.java",
          "**/AndroidManifest.xml",
          "**/res/**",
          "**/src/*/resources/**",
          "gradle.properties",
          "**/gradle.properties",
        )
      )

    val DEFAULT_NEVER_SKIP_PATTERNS: Set<String> =
      unmodifiableSet(
        setOf(
          // root build.gradle.kts and settings.gradle.kts files
          "*.gradle.kts",
          "*.gradle",
          // root gradle.properties file
          "gradle.properties",
          // Version catalogs
          "**/*.versions.toml",
          // Gradle wrapper files
          "**/gradle/wrapper/**",
          "gradle/wrapper/**",
          "gradlew",
          "gradlew.bat",
          "**/gradlew",
          "**/gradlew.bat",
          // buildSrc
          "buildSrc/**",
          // CI
          "**/.github/workflows/**",
        )
      )

    /** Returns a filtered list of [filePaths] that match the given [includePatterns]. */
    fun filterIncludes(filePaths: List<Path>, includePatterns: Collection<String>) =
      filePaths.filter { includePatterns.any { pattern -> pattern.toPathMatcher().matches(it) } }

    /** Returns a filtered list of [filePaths] that do _not_ match the given [includePatterns]. */
    fun filterExcludes(filePaths: List<Path>, excludePatterns: Collection<String>) =
      filePaths.filterNot { excludePatterns.any { pattern -> pattern.toPathMatcher().matches(it) } }

    /** Returns whether any [filePaths] match any [neverSkipPatterns]. */
    fun anyNeverSkip(filePaths: List<Path>, neverSkipPathMatchers: List<PathMatcher>) =
      filePaths.any { path -> neverSkipPathMatchers.any { it.matches(path) } }

    /**
     * A slower, debug-only alternative to [anyNeverSkip] that returns a map of paths to matched
     * [PathMatcher]s. This is useful for debugging as we can indicate the matched pattern.
     */
    fun anyNeverSkipDebug(
      filePaths: List<Path>,
      neverSkipPathMatchers: List<PathMatcher>
    ): Map<Path, PathMatcher?> =
      filePaths.associateWith { path -> neverSkipPathMatchers.find { it.matches(path) } }
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
 * Flips a map. In the context of [ComputeAffectedProjectsTask], we use this to flip a map of
 * projects to their dependencies to a map of projects to the projects that depend on them. We use
 * this to find all affected projects given a seed of changed projects.
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

/** Return a new map with null keys filtered out. */
private fun <K, V : Any> Map<K?, V>.filterNotNullKeys(): Map<K, V> {
  return filterKeys { it != null }.mapKeys { it.key!! }
}

/**
 * Represents a changed project as computed by [ComputeAffectedProjectsTask.changedFiles].
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
    private val testPathMatcher = "**/src/*{test,androidTest}/**".toPathMatcher()
    private val lintBaselinePathMatcher = "**/lint-baseline.xml".toPathMatcher()
  }
}
