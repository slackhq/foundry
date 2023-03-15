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
import kotlin.io.path.nameWithoutExtension
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
 * TODO more granular stuff
 * - not all xml files are equal. baseline.xml is not the same as a resource file
 */
@UntrackedTask(because = "This task modifies build scripts in place.")
internal abstract class ComputeAffectedProjects : DefaultTask() {

  @get:Internal abstract val rootDir: DirectoryProperty

  @get:Input abstract val debug: Property<Boolean>

  @get:Input
  val includePatterns: ListProperty<String> =
    project.objects.listProperty(String::class.java).convention(DEFAULT_INCLUDE_PATTERNS)

  @get:Input
  val neverSkipPatterns: ListProperty<String> =
    project.objects.listProperty(String::class.java).convention(DEFAULT_NEVER_SKIP_PATTERNS)

  @get:Input abstract val dependencyGraph: Property<DependencyGraph.SerializableGraph>

  @get:Option(option = "changed-files", description = "A relative file path to changed_files.txt.")
  @get:Input
  abstract val changedFiles: Property<String>

  @get:OutputDirectory abstract val diagnosticsDir: DirectoryProperty
  @get:OutputFile abstract val outputFile: RegularFileProperty

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
  fun computeTimed() {
    logTimedValue("computation") { compute() }
  }

  @TaskAction
  fun compute() {
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
      // TODO write all projects to file? Or denote a blank file as meaning all? Or don't write a
      //  file?
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
            val gradlePath = projectPath.resolveProjectPath(rootDirPath)
            gradlePath to ChangedProject(projectPath, gradlePath, files.toSet())
          }
      }

    val graph = logTimedValue("creating graph") { DependencyGraph.create(dependencyGraph.get()) }

    val projectsToDependencies: Map<String, Set<String>> =
      logTimedValue("computing dependencies") {
        graph.nodes().associate { node ->
          node.key to node.allDependencies().mapTo(mutableSetOf()) { it.key }
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

        if (!change.onlyTestsAreChanged) {
          addAll(projectsToDependents[path] ?: emptySet())
        }
        //        addAll(graph.subTree(changedProject).findRoot().allDependencies().map { it.key })
      }
    }

    outputFile.get().asFile.writeText(allAffectedProjects.sorted().joinToString("\n"))
  }

  private fun writeDiagnostic(fileName: String, content: () -> String) {
    if (debug.get()) {
      val file = diagnosticsDir.file(fileName).get().asFile
      file.parentFile.mkdirs()
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
        "*.gradle.kts",
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
      val configurationsToLook: Set<String> =
        setOf(
          "api",
          "implementation",
          "ksp",
          "testImplementation",
          "androidTestImplementation",
          "compileOnly"
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
 * `/Users/username/projects/MyApp/app/src/main/kotlin/com/example/myapp/MainActivity.kt`, returns a
 * Gradle project path (e.g. `:app`).
 */
private fun Path.resolveProjectPath(rootDir: Path): String {
  return rootDir.relativize(this).nameWithoutExtension.replace(File.separatorChar, ':')
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
  return findNearestProjectDir(repoRoot, currentDir, cache)
}

private fun findNearestProjectDir(
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
    findNearestProjectDir(repoRoot, currentDir.parent, cache)
  }
}

private fun DependencyGraph.Node.allDependencies(): Set<DependencyGraph.Node> {
  return buildSet {
    addAll(dependsOn)
    for (dependency in dependsOn) {
      addAll(dependency.allDependencies())
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
  val changedPaths: Set<Path>
) {
  /**
   * Returns true if all changed files are in a test directory and therefore do not carry-over to
   * downstream dependents.
   */
  val onlyTestsAreChanged: Boolean
    get() = changedPaths.any(testPathMatcher::matches)

  companion object {
    private val testPathMatcher =
      FileSystems.getDefault().getPathMatcher("glob:**/src/**/*{test,androidTest}/**")
  }
}
