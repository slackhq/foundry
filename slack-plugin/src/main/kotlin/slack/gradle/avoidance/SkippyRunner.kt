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
import com.squareup.moshi.adapter
import kotlin.coroutines.CoroutineContext
import kotlin.time.measureTimedValue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import slack.gradle.avoidance.SkippyConfig.Companion.GLOBAL_TOOL
import slack.gradle.util.JsonTools
import slack.gradle.util.SgpLogger
import slack.gradle.util.flatMapToSet
import slack.gradle.util.flip
import slack.gradle.util.parallelMapNotNull
import slack.gradle.util.readLines
import slack.gradle.util.writeLines
import slack.gradle.util.writeText

internal class SkippyRunner(
  private val outputsDir: Path,
  private val androidTestProjects: Set<String> = emptySet(),
  private val rootDir: Path,
  private val dependencyGraph: DependencyGraph.SerializableGraph,
  private val changedFilesPath: Path,
  private val originalConfigMap: Map<String, SkippyConfig>,
  private val parallelism: Int = originalConfigMap.size,
  private val fs: FileSystem = FileSystem.SYSTEM,
  private val debug: Boolean = false,
  private val logger: SgpLogger = SgpLogger.noop(),
  private val mergeOutputs: Boolean = true,
) {
  init {
    check(fs.exists(rootDir)) { "rootDir does not exist: $rootDir" }
    check(fs.exists(changedFilesPath)) { "changedFilesPath does not exist: $changedFilesPath" }
  }

  internal suspend fun run(context: CoroutineContext) {
    outputsDir.apply {
      if (fs.exists(this)) {
        fs.deleteRecursively(this)
      }
      fs.createDirectories(this)
    }

    val configMap = originalConfigMap.toMutableMap()
    // Extract the global config and apply it to each of the tools
    val configs =
      if (configMap.size == 1) {
        configMap.values
      } else {
        // No per-service configs, just use the global one
        val globalConfig = configMap.remove(GLOBAL_TOOL) ?: error("No global config!")
        configMap.values.map { config -> config.overlayWith(globalConfig) }
      }

    val baseOutputDir = outputsDir
    withContext(context) {
      // Pre-compute dependency graph shape
      val resolvedDependencyGraph =
        logTimedValue("diagnostics", "resolving graph") { DependencyGraph.create(dependencyGraph) }
      val diagnostics =
        if (debug) {
          val diagnosticsDir = outputsDir / "diagnostics"
          fs.createDirectories(diagnosticsDir)
          DiagnosticWriter { name, content ->
            val path = diagnosticsDir / name
            logger.lifecycle("writing diagnostic file: $path")
            fs.write(path) { writeUtf8(content()) }
          }
        } else {
          DiagnosticWriter.NoOp
        }

      val shallowProjectsToDependencies: Map<String, Set<String>> =
        logTimedValue("diagnostics", "computing shallow dependencies") {
          resolvedDependencyGraph.nodes().associate { node ->
            val dependencies = mutableSetOf<DependencyGraph.Node>()
            node.visitDependencies(dependencies)
            node.key to dependencies.mapTo(mutableSetOf()) { it.key }
          }
        }

      diagnostics.write("shallowProjectsToDependencies.txt") {
        buildString {
          for ((project, dependencies) in shallowProjectsToDependencies.toSortedMap()) {
            appendLine(project)
            for (dep in dependencies.sorted()) {
              appendLine("-> $dep")
            }
          }
        }
      }

      val projectsToDependencies =
        logTimedValue("diagnostics", "computing full dependencies") {
          shallowProjectsToDependencies.mapValues { (project, _) ->
            getAllDependencies(project, shallowProjectsToDependencies)
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

      val projectsToDependents =
        logTimedValue("diagnostics", "computing dependents", projectsToDependencies::flip)

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

      val dependencyMetadata = DependencyMetadata(projectsToDependents, projectsToDependencies)

      val outputs =
        configs.parallelMapNotNull(parallelism) { config ->
          computeForTool(config, baseOutputDir, dependencyMetadata)
        }
      if (mergeOutputs) {
        // Always init the merged output dir so it clears any existing files
        val mergedOutput = WritableSkippyOutput("merged", baseOutputDir, fs)
        // If any of the outputs are null, then we should skip the merge as a
        // never-skip scenario for one means never-skip for all
        if (outputs.size == configs.size) {
          val mergedAffectedProjects = async {
            outputs.flatMapToSet { it.affectedProjectsFile.readLines(fs) }.toSortedSet()
          }
          val mergedAffectedAndroidTestProjects = async {
            outputs.flatMapToSet { it.affectedAndroidTestProjectsFile.readLines(fs) }.toSortedSet()
          }
          val mergedFocusProjects = async {
            outputs.flatMapToSet { it.outputFocusFile.readLines(fs) }.toSortedSet()
          }
          val (affectedProjects, affectedAndroidTestProjects, focusProjects) =
            listOf(
                mergedAffectedProjects,
                mergedAffectedAndroidTestProjects,
                mergedFocusProjects,
              )
              .awaitAll()
          mergedOutput.affectedProjectsFile.writeLines(affectedProjects, fs)
          mergedOutput.affectedAndroidTestProjectsFile.writeLines(affectedAndroidTestProjects, fs)
          mergedOutput.outputFocusFile.writeLines(focusProjects, fs)
        }
      }
    }
  }

  /** Computes a [SkippyOutput] for the given [config]. */
  private fun computeForTool(
    config: SkippyConfig,
    outputDir: Path,
    dependencyMetadata: DependencyMetadata,
  ): SkippyOutput? {
    val tool = config.tool
    val skippyOutputs = WritableSkippyOutput(tool, outputDir, fs)
    val prefixLogger = SgpLogger.prefix("$LOG_PREFIX[$tool]", logger)
    val diagnostics =
      if (debug) {
        val diagnosticsDir = outputsDir / tool / "diagnostics"
        fs.createDirectories(diagnosticsDir)
        DiagnosticWriter { name, content ->
          val path = diagnosticsDir / name
          prefixLogger.lifecycle("writing diagnostic file: $path")
          fs.write(path) { writeUtf8(content()) }
        }
      } else {
        DiagnosticWriter.NoOp
      }
    return logTimedValue(tool, "SkippyRunner computation") {
      // Write the config to a diagnostic file
      diagnostics.write("config.json") {
        JsonTools.MOSHI.adapter<SkippyConfig>().indent("  ").toJson(config)
      }

      val (affectedProjects, focusProjects, affectedAndroidTestProjects) =
        AffectedProjectsComputer(
            rootDirPath = rootDir,
            dependencyMetadata = dependencyMetadata,
            config = config,
            androidTestProjects = androidTestProjects,
            debug = debug,
            diagnostics = diagnostics,
            changedFilePaths =
              logTimedValue(tool, "reading changed files") {
                log(tool, "reading changed files from: $changedFilesPath")
                changedFilesPath.readLines(fs).map { it.trim().toPath(normalize = true) }
              },
            logger = prefixLogger,
            fileSystem = fs,
          )
          .compute() ?: return@logTimedValue null

      // Generate affected_projects.txt
      log(tool, "writing affected projects to: ${skippyOutputs.affectedProjectsFile}")
      skippyOutputs.affectedProjectsFile.writeLines(affectedProjects.sorted(), fs)

      // Generate affected_android_test_projects.txt
      log(
        tool,
        "writing affected androidTest projects to: ${skippyOutputs.affectedAndroidTestProjectsFile}"
      )
      skippyOutputs.affectedAndroidTestProjectsFile.writeLines(
        affectedAndroidTestProjects.sorted(),
        fs
      )

      // Generate .focus settings file
      log(tool, "writing focus settings to: ${skippyOutputs.outputFocusFile}")
      skippyOutputs.outputFocusFile.writeText(
        focusProjects.joinToString("\n") { "include(\"$it\")" },
        fs
      )

      skippyOutputs.delegate
    }
  }

  private fun log(tool: String, message: String) {
    val withPrefix = "${LOG_PREFIX}[$tool] $message"
    // counter-intuitive to read but lifecycle is preferable when actively debugging, whereas
    // debug() only logs quietly unless --debug is used
    if (debug) {
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

  private fun DependencyGraph.Node.visitDependencies(setToAddTo: MutableSet<DependencyGraph.Node>) {
    for (dependency in dependsOn) {
      if (!setToAddTo.add(dependency)) {
        // Only add transitives if we haven't already seen this dependency.
        dependency.visitDependencies(setToAddTo)
      }
    }
  }

  companion object {
    private const val LOG_PREFIX = "[Skippy]"

    /** Returns a deep set of all dependencies for the given [project], including transitive. */
    // TODO future optimization could be to include previously-computed project dependencies as a
    //  cache.
    internal fun getAllDependencies(
      project: String,
      projectsToDependencies: Map<String, Set<String>>,
      includeSelf: Boolean = false,
    ): Set<String> {
      val result = mutableSetOf<String>()
      fun addDependent(project: String) {
        if (result.add(project)) {
          projectsToDependencies[project]?.forEach(::addDependent)
        }
      }

      addDependent(project)

      if (!includeSelf) {
        result.remove(project)
      }
      return result
    }
  }
}
