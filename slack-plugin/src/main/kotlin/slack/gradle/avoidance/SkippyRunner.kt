package slack.gradle.avoidance

import com.jraska.module.graph.DependencyGraph
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeLines
import kotlin.io.path.writeText
import kotlin.time.measureTimedValue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import slack.gradle.util.SgpLogger
import slack.gradle.util.flatMapToSet
import slack.gradle.util.parallelMapNotNull

@OptIn(ExperimentalPathApi::class)
internal class SkippyRunner(
  private val debug: Boolean,
  private val logger: SgpLogger,
  private val mergeOutputs: Boolean,
  private val outputsDir: Path,
  private val diagnosticsDir: Path,
  private val androidTestProjects: Set<String>,
  private val rootDir: Path,
  private val dependencyGraph: DependencyGraph.SerializableGraph,
  private val diagnosticWriter: DiagnosticWriter,
  private val changedFilesPath: Path,
  private val originalConfigMap: Map<String, SkippyConfig>,
) {
  companion object {
    private const val LOG_PREFIX = "[Skippy]"
  }

  init {
    check(rootDir.exists()) { "rootDir does not exist: $rootDir" }
    check(changedFilesPath.exists()) { "changedFilesPath does not exist: $changedFilesPath" }
  }

  internal suspend fun run(context: CoroutineContext) {
    outputsDir.apply {
      if (exists()) {
        deleteRecursively()
      }
      createDirectories()
    }
    if (debug) {
      diagnosticsDir.apply {
        if (exists()) {
          deleteRecursively()
        }
        createDirectories()
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

  /** Computes a [SkippyOutput] for the given [config]. */
  @OptIn(ExperimentalPathApi::class)
  private fun computeForTool(config: SkippyConfig, outputDir: Path): SkippyOutput? {
    val tool = config.tool
    val skippyOutputs = WritableSkippyOutput(tool, outputDir)
    val prefixLogger = SgpLogger.prefix("$LOG_PREFIX[$tool]", logger)
    return logTimedValue(tool, "gradle task computation") {
      val (affectedProjects, focusProjects, affectedAndroidTestProjects) =
        AffectedProjectsComputer(
            rootDirPath = rootDir.toOkioPath(normalize = true),
            dependencyGraph = {
              logTimedValue(tool, "creating dependency graph") {
                DependencyGraph.create(dependencyGraph)
              }
            },
            config = config,
            androidTestProjects = androidTestProjects,
            debug = debug,
            diagnostics = diagnosticWriter,
            changedFilePaths =
              logTimedValue(tool, "reading changed files") {
                log(tool, "reading changed files from: $changedFilesPath")
                changedFilesPath.readLines().map { it.trim().toPath(normalize = true) }
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
