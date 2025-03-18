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
package foundry.gradle.avoidance

import com.jraska.module.graph.DependencyGraph
import foundry.common.FoundryLogger
import foundry.gradle.FoundryProperties
import foundry.gradle.FoundryShared
import foundry.gradle.properties.setDisallowChanges
import foundry.gradle.property
import foundry.gradle.util.gradle
import foundry.skippy.AffectedProjectsComputer
import foundry.skippy.SkippyRunner
import java.io.ObjectInputStream
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option

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
public abstract class ComputeAffectedProjectsTask : DefaultTask() {

  @get:Input
  public val debug: Property<Boolean> = project.objects.property<Boolean>().convention(false)

  @get:Input
  public val mergeOutputs: Property<Boolean> = project.objects.property<Boolean>().convention(true)

  @get:Input
  public val configs: NamedDomainObjectContainer<SkippyGradleConfig> =
    project.objects.domainObjectContainer(SkippyGradleConfig::class.java)

  @get:Input
  public val computeInParallel: Property<Boolean> =
    project.objects.property<Boolean>().convention(true)

  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  public abstract val androidTestProjectPathsFile: RegularFileProperty

  /**
   * A relative (to the repo root) path to a changed_files.txt that contains a newline-delimited
   * list of changed files. This is usually computed from a GitHub PR's changed files.
   */
  @get:Option(option = "changed-files", description = "A relative file path to changed_files.txt.")
  @get:Input
  public abstract val changedFiles: Property<String>

  /** A serialized dependency graph. */
  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  public abstract val serializedDependencyGraph: RegularFileProperty

  /** Output dir for skippy outputs. */
  @get:OutputDirectory public abstract val outputsDir: DirectoryProperty

  /*
   * Internal properties.
   */

  /** Root repo directory. Used to compute relative paths and not considered an input. */
  @get:Internal internal abstract val rootDir: DirectoryProperty

  init {
    group = FoundryShared.FOUNDRY_TASK_GROUP
    description = "Computes affected projects and writes output files to an output directory."
  }

  @OptIn(DelicateCoroutinesApi::class)
  @TaskAction
  internal fun compute() {
    val rootDirPath = rootDir.get().asFile.toOkioPath()
    val parallelism =
      if (computeInParallel.get() && configs.size > 1) {
        configs.size
      } else {
        1
      }
    val androidTestProjects =
      androidTestProjectPathsFile.asFile.get().readLines().map { it.trim() }.toSet()
    val dependencyGraph =
      ObjectInputStream(serializedDependencyGraph.asFile.get().inputStream()).use {
        it.readObject() as DependencyGraph.SerializableGraph
      }
    val body: suspend (context: CoroutineContext) -> Unit = { context ->
      SkippyRunner(
          debug = debug.get(),
          logger = FoundryLogger.gradle(logger),
          mergeOutputs = mergeOutputs.get(),
          outputsDir = outputsDir.get().asFile.toOkioPath(),
          androidTestProjects = androidTestProjects,
          rootDir = rootDirPath,
          parallelism = parallelism,
          fs = FileSystem.SYSTEM,
          dependencyGraph = dependencyGraph,
          changedFilesPath = rootDirPath.resolve(changedFiles.get()),
          originalConfigMap =
            configs.map(SkippyGradleConfig::asSkippyConfig).associateBy { it.tool },
        )
        .run(context)
    }

    runBlocking {
      if (parallelism == 1) {
        body(Dispatchers.Unconfined)
      } else {
        logger.lifecycle("Running $parallelism configs in parallel")
        newFixedThreadPoolContext(3, "computeAffectedProjects").use { dispatcher ->
          body(dispatcher)
        }
      }
    }
  }

  internal companion object {
    private const val NAME = "computeAffectedProjects"

    fun register(
      rootProject: Project,
      foundryProperties: FoundryProperties,
      dependencyGraphProvider: Provider<RegularFile>,
      androidTestProjectPathsProvider: Provider<RegularFile>,
    ): TaskProvider<ComputeAffectedProjectsTask> {
      val extension =
        rootProject.extensions.create("skippy", SkippyExtension::class.java, foundryProperties)

      return rootProject.tasks.register(NAME, ComputeAffectedProjectsTask::class.java) {
        debug.setDisallowChanges(extension.debug)
        mergeOutputs.setDisallowChanges(extension.mergeOutputs)
        computeInParallel.setDisallowChanges(extension.computeInParallel)
        configs.addAll(extension.configs)
        rootDir.setDisallowChanges(project.layout.projectDirectory)
        serializedDependencyGraph.setDisallowChanges(dependencyGraphProvider)
        outputsDir.setDisallowChanges(project.layout.buildDirectory.dir("skippy"))
        androidTestProjectPathsFile.setDisallowChanges(androidTestProjectPathsProvider)
      }
    }
  }
}
