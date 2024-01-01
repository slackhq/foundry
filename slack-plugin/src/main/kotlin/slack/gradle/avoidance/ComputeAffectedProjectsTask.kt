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
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option
import slack.gradle.SlackProperties
import slack.gradle.artifacts.Resolver
import slack.gradle.artifacts.SgpArtifacts
import slack.gradle.property
import slack.gradle.setProperty
import slack.gradle.util.SgpLogger
import slack.gradle.util.setDisallowChanges

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

  /**
   * Consumed artifacts of project paths that produce androidTest APKs. Each file will just have one line
   * that contains a project path.
   */
  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:Input
  public abstract val androidTestProjectInputs: ConfigurableFileCollection

  /**
   * A relative (to the repo root) path to a changed_files.txt that contains a newline-delimited
   * list of changed files. This is usually computed from a GitHub PR's changed files.
   */
  @get:Option(option = "changed-files", description = "A relative file path to changed_files.txt.")
  @get:Input
  public abstract val changedFiles: Property<String>

  /** Output dir for skippy outputs. */
  @get:OutputDirectory public abstract val outputsDir: DirectoryProperty

  /*
   * Internal properties.
   */

  /** Root repo directory. Used to compute relative paths and not considered an input. */
  @get:Internal internal abstract val rootDir: DirectoryProperty

  @get:Input internal abstract val dependencyGraph: Property<DependencyGraph.SerializableGraph>

  init {
    group = "slack"
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
      androidTestProjectInputs.map { it.readText().trim() }.toSet()
    val body: suspend (context: CoroutineContext) -> Unit = { context ->
      SkippyRunner(
          debug = debug.get(),
          logger = SgpLogger.gradle(logger),
          mergeOutputs = mergeOutputs.get(),
          outputsDir = outputsDir.get().asFile.toOkioPath(),
          androidTestProjects = androidTestProjects,
          rootDir = rootDirPath,
          parallelism = parallelism,
          fs = FileSystem.SYSTEM,
          dependencyGraph = dependencyGraph.get(),
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
    internal const val NAME = "computeAffectedProjects"
    private val DEFAULT_CONFIGURATIONS =
      setOf(
        "androidTestImplementation",
        "annotationProcessor",
        "api",
        "compileOnly",
        "debugApi",
        "debugImplementation",
        "implementation",
        "kapt",
        "kotlinCompilerPluginClasspath",
        "ksp",
        "releaseApi",
        "releaseImplementation",
        "testImplementation",
      )

    fun register(
      rootProject: Project,
      slackProperties: SlackProperties
    ): TaskProvider<ComputeAffectedProjectsTask> {
      val extension =
        rootProject.extensions.create("skippy", SkippyExtension::class.java, slackProperties)
      val configurationsToLook by lazy {
        val providedConfigs = slackProperties.affectedProjectConfigurations
        providedConfigs?.splitToSequence(',')?.toSet()?.let { providedConfigSet ->
          if (slackProperties.buildUponDefaultAffectedProjectConfigurations) {
            DEFAULT_CONFIGURATIONS + providedConfigSet
          } else {
            providedConfigSet
          }
        } ?: DEFAULT_CONFIGURATIONS
      }

      val moduleGraph by lazy {
        GradleDependencyGraphFactory.create(rootProject, configurationsToLook).serializableGraph()
      }

      val androidTestApksResolver = Resolver.interProjectResolver(
        rootProject,
        SgpArtifacts.Kind.SKIPPY_ANDROID_TEST_PROJECT,
      )

      return rootProject.tasks.register(NAME, ComputeAffectedProjectsTask::class.java) {
        debug.setDisallowChanges(extension.debug)
        mergeOutputs.setDisallowChanges(extension.mergeOutputs)
        computeInParallel.setDisallowChanges(extension.computeInParallel)
        configs.addAll(extension.configs)
        rootDir.setDisallowChanges(project.layout.projectDirectory)
        dependencyGraph.setDisallowChanges(rootProject.provider { moduleGraph })
        outputsDir.setDisallowChanges(project.layout.buildDirectory.dir("skippy"))
        androidTestProjectInputs.from(androidTestApksResolver.artifactView())
        // Overrides of includes/neverSkippable patterns should be done in the consuming project
        // directly
      }
    }
  }
}
