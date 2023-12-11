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
import kotlin.coroutines.CoroutineContext
import kotlin.time.measureTimedValue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import slack.gradle.util.SgpLogger
import slack.gradle.util.flatMapToSet
import slack.gradle.util.parallelMapNotNull
import slack.gradle.util.readLines
import slack.gradle.util.writeLines
import slack.gradle.util.writeText

internal class SkippyRunner(
  private val outputsDir: Path,
  private val diagnosticsDir: Path,
  private val androidTestProjects: Set<String> = emptySet(),
  private val rootDir: Path,
  private val dependencyGraph: DependencyGraph.SerializableGraph,
  private val changedFilesPath: Path,
  private val originalConfigMap: Map<String, SkippyConfig>,
  private val fs: FileSystem = FileSystem.SYSTEM,
  private val debug: Boolean = false,
  private val logger: SgpLogger = SgpLogger.noop(),
  private val mergeOutputs: Boolean = true,
  private val diagnostics: DiagnosticWriter = DiagnosticWriter.NoOp,
) {
  companion object {
    private const val LOG_PREFIX = "[Skippy]"
  }

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
    if (debug) {
      diagnosticsDir.apply {
        if (fs.exists(this)) {
          fs.deleteRecursively(this)
        }
        fs.createDirectories(this)
      }
    }

    val configMap = originalConfigMap.toMutableMap()
    // Extract the global config and apply it to each of the tools
    val configs =
      if (configMap.size == 1) {
        configMap.values
      } else {
        // No per-service configs, just use the global one
        val globalConfig =
          configMap.remove(SkippyExtension.GLOBAL_TOOL) ?: error("No global config!")
        configMap.values.map { config -> config.overlayWith(globalConfig) }
      }

    val baseOutputDir = outputsDir
    withContext(context) {
      val outputs =
        configs.parallelMapNotNull(configs.size) { config -> computeForTool(config, baseOutputDir) }
      if (mergeOutputs) {
        val mergedOutput = WritableSkippyOutput("merged", baseOutputDir, fs)
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

  /** Computes a [SkippyOutput] for the given [config]. */
  private fun computeForTool(config: SkippyConfig, outputDir: Path): SkippyOutput? {
    val tool = config.tool
    val skippyOutputs = WritableSkippyOutput(tool, outputDir, fs)
    val prefixLogger = SgpLogger.prefix("$LOG_PREFIX[$tool]", logger)
    return logTimedValue(tool, "gradle task computation") {
      // TODO write the config to a diagnostic too
      val (affectedProjects, focusProjects, affectedAndroidTestProjects) =
        AffectedProjectsComputer(
            rootDirPath = rootDir,
            dependencyGraph = {
              logTimedValue(tool, "creating dependency graph") {
                DependencyGraph.create(dependencyGraph)
              }
            },
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
}
