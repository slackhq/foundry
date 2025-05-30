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
package foundry.skippy

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import com.jraska.module.graph.DependencyGraph
import foundry.common.FoundryLogger
import foundry.common.json.JsonTools
import java.io.ObjectInputStream
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.inputStream
import kotlin.io.path.readLines
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.newFixedThreadPoolContext
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/**
 * @see AffectedProjectsComputer for most of the salient docs! The inputs in this CLI more or less
 *   match 1:1 to the properties of that class.
 */
public class ComputeAffectedProjectsCli : SuspendingCliktCommand() {

  override fun help(context: Context): String =
    "Computes affected projects and writes output files to an output directory."

  private val debug: Boolean by
    option("--debug", help = "Enable debug logging.").flag(default = false)

  private val mergeOutputs: Boolean by
    option("--merge-outputs", help = "Merge outputs from all configs into a single /merged dir.")
      .flag("--no-merge-outputs", default = true)

  private val config: Path by
    option(
        "--config",
        help =
          "Path to a config file that contains a mapping of tool names to SkippyConfig objects.",
      )
      .path(mustExist = true, canBeDir = false, mustBeReadable = true)
      .required()

  private val computeInParallel: Boolean by
    option("--parallel", help = "Compute affected projects in parallel.")
      .flag("--no-parallel", default = true)

  private val changedFiles: Path by
    option(
        "--changed-files",
        help =
          "A relative (to the repo root) path to a changed_files.txt that contains a newline-delimited list of changed files. This is usually computed from a GitHub PR's changed files.",
      )
      .path(mustExist = true, canBeDir = false, mustBeReadable = true)
      .required()

  private val outputsDir by
    option("--outputs-dir", "-o", help = "Output directory for skippy outputs.")
      .path(canBeFile = false)
      .required()

  private val rootDir by
    option("--root-dir", help = "Root repo directory. Used to compute relative paths.")
      .path(mustExist = true, canBeFile = false, mustBeReadable = true)
      .required()

  private val serializedDependencyGraph by
    option(
        "--dependency-graph",
        help =
          "Path to a serialized dependency graph file. Only necessary if using the legacy mode.",
      )
      .path(mustExist = true, canBeDir = false, mustBeReadable = true)

  private val graphEdges by
    option(
        "--dependency-graph-edges",
        help =
          "Path to a list of dependency graph edges. In the format of newline-delimited :path1,:path2",
      )
      .path(mustExist = true, canBeDir = false, mustBeReadable = true)

  private val androidTestProjectPaths by
    option(
        "--android-test-project-paths",
        help =
          "Path to a file that contains a newline-delimited list of project paths that produce androidTest APKs.",
      )
      .path(mustExist = true, canBeDir = false, mustBeReadable = true)

  private val logger = FoundryLogger.clikt(this)

  @OptIn(DelicateCoroutinesApi::class, ExperimentalPathApi::class)
  override suspend fun run() {
    val dependencyGraph =
      if (serializedDependencyGraph != null) {
        echo("--dependency-graph is deprecated. Use --dependency-graph-edges instead.", err = true)
        ObjectInputStream(serializedDependencyGraph!!.inputStream())
          .use { it.readObject() as DependencyGraph.SerializableGraph }
          .let { DependencyGraph.create(it) }
      } else if (graphEdges != null) {
        // Compute the DAG directly from edges produced by another tool
        val allEdges =
          graphEdges!!.readLines().map { line ->
            line.split(',').let { (source, dependency) -> source to dependency }
          }
        DependencyGraph.create(allEdges)
      } else {
        error("One of '--dependency-graph' or '--dependency-graph-edges' must be specified.")
      }

    val rootDirPath = rootDir.toOkioPath()
    val configs = JsonTools.fromJson<List<SkippyConfig>>(config).associateBy { it.tool }
    val parallelism =
      if (computeInParallel && configs.size > 1) {
        configs.size
      } else {
        1
      }
    val androidTestProjects = androidTestProjectPaths?.readLines().orEmpty().toSet()
    val body: suspend (context: CoroutineContext) -> Unit = { context ->
      SkippyRunner(
          debug = debug,
          logger = logger,
          mergeOutputs = mergeOutputs,
          outputsDir = outputsDir.toOkioPath(),
          androidTestProjects = androidTestProjects,
          rootDir = rootDirPath,
          parallelism = parallelism,
          fs = FileSystem.SYSTEM,
          dependencyGraph = dependencyGraph,
          changedFilesPath = rootDirPath.resolve(changedFiles.toOkioPath()),
          originalConfigMap = configs,
        )
        .run(context)
    }

    if (parallelism == 1) {
      body(Dispatchers.Unconfined)
    } else {
      logger.lifecycle("Running $parallelism configs in parallel")
      newFixedThreadPoolContext(3, "computeAffectedProjects").use { dispatcher -> body(dispatcher) }
    }
  }
}
