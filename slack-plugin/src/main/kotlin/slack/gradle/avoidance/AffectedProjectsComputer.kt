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
import java.io.File
import kotlin.io.path.exists
import kotlin.time.measureTimedValue
import okio.FileSystem
import okio.Path
import slack.gradle.util.SgpLogger

internal class AffectedProjectsComputer(
  private val rootDirPath: Path,
  private val dependencyGraph: () -> DependencyGraph,
  private val changedFilePaths: List<Path>,
  private val diagnostics: DiagnosticWriter = DiagnosticWriter.NoOp,
  private val includePatterns: List<String> = DEFAULT_INCLUDE_PATTERNS,
  private val neverSkipPatterns: List<String> = DEFAULT_NEVER_SKIP_PATTERNS,
  private val debug: Boolean = false,
  private val fileSystem: FileSystem = FileSystem.SYSTEM,
  private val logger: SgpLogger = SgpLogger.noop()
) {
  fun compute(): AffectedProjectsResult? {
    return logTimedValue("full computation") { computeImpl() }
  }

  private fun computeImpl(): AffectedProjectsResult? {
    log("changedFilePaths: $changedFilePaths")

    log("includePatterns: $includePatterns")
    val filteredChangedFilePaths =
      logTimedValue("filtering changed files") {
        changedFilePaths.filter {
          includePatterns.any { pattern -> pattern.toPathMatcher().matches(it) }
        }
      }
    log("filteredChangedFilePaths: $filteredChangedFilePaths")

    val neverSkipPathMatchers =
      logTimedValue("creating path matchers") { neverSkipPatterns.map { it.toPathMatcher() } }
    log("neverSkipPatterns: $neverSkipPatterns")

    if (debug) {
      // Do a slower, more verbose check in debug
      val pathsWithSkippability =
        logTimedValue("checking for non-skippable files") {
          filteredChangedFilePaths.associateWith { path ->
            neverSkipPathMatchers.find { it.matches(path) }
          }
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
      if (filteredChangedFilePaths.any { path -> neverSkipPathMatchers.any { it.matches(path) } }) {
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
          .groupBy { it.findNearestProjectDir(rootDirPath, nearestProjectCache, fileSystem) }
          .filterNotNullKeys()
          .entries
          .associate { (projectPath, files) ->
            val gradlePath = ":${projectPath.toString().replace(File.separatorChar, ':')}"
            gradlePath to ChangedProject(projectPath, gradlePath, files.toSet())
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

  companion object {
    internal val DEFAULT_INCLUDE_PATTERNS =
      listOf(
        "**/*.kt",
        "*.gradle.kts",
        "**/*.gradle.kts",
        "**/*.java",
        "**/AndroidManifest.xml",
        "**/res/**",
        "**/gradle.properties",
      )

    internal val DEFAULT_NEVER_SKIP_PATTERNS =
      listOf(
        // root build.gradle.kts and settings.gradle.kts files
        "*.gradle.kts",
        // root gradle.properties file
        "gradle.properties",
        "**/*.versions.toml",
      )
  }
}

/**
 * Given a file path like
 * `/Users/username/projects/MyApp/app/src/main/kotlin/com/example/myapp/MainActivity.kt`, returns
 * the nearest Gradle project [Path] like `/Users/username/projects/MyApp/app`.
 */
private fun Path.findNearestProjectDir(
  repoRoot: Path,
  cache: MutableMap<Path, Path?>,
  fileSystem: FileSystem,
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
      isRegularFile(fileSystem) -> parent
      isDirectory(fileSystem) -> this
      else -> error("Unsupported file type: $this")
    }
  return findNearestProjectDirRecursive(repoRoot, currentDir, cache)
}

private fun Path.isRegularFile(fileSystem: FileSystem): Boolean =
  fileSystem.metadataOrNull(this)?.isRegularFile == true

private fun Path.isDirectory(fileSystem: FileSystem): Boolean =
  fileSystem.metadataOrNull(this)?.isDirectory == true

private fun Path.exists(): Boolean = toNioPath().exists()

private fun findNearestProjectDirRecursive(
  repoRoot: Path,
  currentDir: Path?,
  cache: MutableMap<Path, Path?>
): Path? {
  if (currentDir == null || currentDir == repoRoot) {
    error("Could not find build.gradle(.kts) for $currentDir")
  }

  return cache.getOrPut(currentDir) {
    // Note the dir may not exist, but that's ok because we still want to check its parents
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
