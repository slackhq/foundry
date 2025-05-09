/*
 * Copyright (C) 2024 Slack Technologies, LLC
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
package foundry.gradle.topography

import foundry.cli.walkEachFile
import foundry.common.json.JsonTools
import foundry.gradle.FoundryExtension
import foundry.gradle.FoundryProperties
import foundry.gradle.FoundryShared
import foundry.gradle.artifacts.FoundryArtifact
import foundry.gradle.artifacts.Publisher
import foundry.gradle.artifacts.Resolver
import foundry.gradle.avoidance.SkippyArtifacts
import foundry.gradle.capitalizeUS
import foundry.gradle.properties.setDisallowChanges
import foundry.gradle.register
import foundry.gradle.serviceOf
import foundry.gradle.tasks.SimpleFileProducerTask
import foundry.gradle.tasks.SimpleFilesConsumerTask
import foundry.gradle.tasks.dependsOnSourceGeneratingTasks
import foundry.gradle.tasks.publish
import foundry.gradle.util.toJson
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.useLines
import kotlin.io.path.writeText
import kotlin.jvm.optionals.getOrNull
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.plugins.PluginRegistry
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.Problems
import org.gradle.api.problems.Severity
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.options.Option
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.work.DisableCachingByDefault

private fun MapProperty<String, Boolean>.put(feature: ModuleFeature, provider: Provider<Boolean>) {
  put(feature.name, provider.orElse(false))
}

internal object ModuleTopographyTasks {
  fun configureRootProject(project: Project) {
    val resolver = Resolver.interProjectResolver(project, FoundryArtifact.SkippyValidateTopography)
    SimpleFilesConsumerTask.registerOrConfigure(
      project,
      ValidateModuleTopographyTask.GLOBAL_CI_NAME,
      description = "Global lifecycle task to run all dependent validateModuleTopography tasks.",
      inputFiles = resolver.artifactView(),
    )
  }

  fun configureSubproject(
    project: Project,
    foundryExtension: FoundryExtension,
    foundryProperties: FoundryProperties,
    affectedProjects: Set<String>?,
  ): TaskProvider<ModuleTopographyTask> {
    val task =
      project.tasks.register<ModuleTopographyTask>("moduleTopography") {
        projectName.set(project.name)
        projectPath.set(project.path)
        features.put(
          DefaultFeatures.MoshiCodeGen,
          foundryExtension.featuresHandler.moshiHandler.moshiCodegen,
        )
        features.put(
          DefaultFeatures.CircuitInject,
          foundryExtension.featuresHandler.circuitHandler.codegen,
        )
        features.put(
          DefaultFeatures.Dagger,
          foundryExtension.featuresHandler.diHandler.enabled.zip(
            foundryExtension.featuresHandler.diHandler.runtimeOnly
          ) { enabled, runtimeOnly ->
            enabled && !runtimeOnly
          },
        )
        features.put(
          DefaultFeatures.DaggerCompiler,
          foundryExtension.featuresHandler.diHandler.useDaggerCompiler,
        )
        features.put(
          DefaultFeatures.Compose,
          foundryExtension.featuresHandler.composeHandler.enableCompiler,
        )
        features.put(
          DefaultFeatures.AndroidTest,
          foundryExtension.androidHandler.featuresHandler.androidTest,
        )
        features.put(
          DefaultFeatures.Robolectric,
          foundryExtension.androidHandler.featuresHandler.robolectric,
        )
        features.put(
          DefaultFeatures.ViewBinding,
          project.provider { foundryExtension.androidHandler.featuresHandler.viewBindingEnabled() },
        )
        topographyOutputFile.setDisallowChanges(
          project.layout.buildDirectory.file("foundry/topography/model/topography.json")
        )
      }

    // Depend on source-gen tasks
    // Don't depend on compiler tasks. Technically doesn't cover javac apt but tbh we don't really
    // support that
    task.dependsOnSourceGeneratingTasks(project, includeCompilerTasks = false)

    // No easy way to query all plugin IDs, so we use an internal Gradle API
    val pluginRegistry = project.serviceOf<PluginRegistry>()
    project.plugins.configureEach {
      val pluginType = this::class.java
      val id = pluginRegistry.findPluginForClass(pluginType).getOrNull()?.id
      if (id == null) {
        project.logger.debug("Could not read plugin ID for type '$pluginType'")
        return@configureEach
      }
      project.logger.debug("Reading plugin ID '$id' for type '$pluginType'")
      project.pluginManager.withPlugin(id) { task.configure { pluginsProperty.add(id) } }
    }

    ValidateModuleTopographyTask.register(project, task, foundryProperties, affectedProjects)
    return task
  }
}

@CacheableTask
public abstract class ModuleTopographyTask : DefaultTask() {
  @get:Input public abstract val projectName: Property<String>

  @get:Input public abstract val projectPath: Property<String>

  @get:Input @get:Optional public abstract val features: MapProperty<String, Boolean>

  @get:Input @get:Optional public abstract val pluginsProperty: SetProperty<String>

  // TODO source DAGP files to check usage of Android APIs?

  @get:OutputFile public abstract val topographyOutputFile: RegularFileProperty

  init {
    group = FoundryShared.FOUNDRY_TASK_GROUP
  }

  @TaskAction
  public fun compute() {
    val plugins = pluginsProperty.getOrElse(emptySet())

    val topography =
      ModuleTopography(
        name = projectName.get(),
        gradlePath = projectPath.get(),
        features =
          features.getOrElse(emptyMap()).filterValues { enabled -> enabled }.keys.toSortedSet(),
        plugins = plugins.toSortedSet(),
      )

    topography.writeJsonTo(topographyOutputFile, prettyPrint = true)
  }
}

@DisableCachingByDefault
public abstract class ValidateModuleTopographyTask @Inject constructor(problems: Problems) :
  DefaultTask() {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  @get:Optional
  public abstract val featuresConfigFile: RegularFileProperty

  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  public abstract val topographyJson: RegularFileProperty

  @get:Optional
  @get:Option(option = "auto-fix", description = "Enables auto-fixing build files")
  @get:Input
  public abstract val autoFix: Property<Boolean>

  @get:Internal public abstract val projectDirProperty: DirectoryProperty
  @get:Internal public abstract val rootDirProperty: DirectoryProperty

  @get:OutputFile public abstract val modifiedBuildFile: RegularFileProperty
  @get:OutputFile public abstract val featuresToRemoveOutputFile: RegularFileProperty

  private val problemReporter = problems.reporter

  init {
    group = FoundryShared.FOUNDRY_TASK_GROUP
    @Suppress("LeakingThis")
    notCompatibleWithConfigurationCache("This task modified build files in place")
    @Suppress("LeakingThis") doNotTrackState("This task modified build files in place")
  }

  @OptIn(ExperimentalPathApi::class)
  @TaskAction
  public fun validate() {
    val topography = ModuleTopography.from(topographyJson)
    val loadedFeatures =
      featuresConfigFile.asFile
        .map { ModuleFeaturesConfig.load(it.toPath()) }
        .getOrElse(ModuleFeaturesConfig.DEFAULT)
        .loadFeatures()
    val features = buildSet {
      addAll(topography.features.map { featureKey -> loadedFeatures.getValue(featureKey) })
      // Include plugin-specific features to the check here
      addAll(loadedFeatures.filterValues { it.matchingPlugin in topography.plugins }.values)
    }
    val featuresToRemove = mutableSetOf<ModuleFeature>()

    val projectDir = projectDirProperty.asFile.get().toPath()
    val srcsDir = projectDir.resolve("src")

    val buildFile = projectDir.resolve("build.gradle.kts")
    var buildFileText = buildFile.readText()
    val initialBuildFileHash = buildFileText.hashCode()

    for (feature in features) {
      val initialRemoveSize = featuresToRemove.size
      feature.matchingSourcesDir?.let { matchingSrcsDir ->
        if (projectDir.resolve(matchingSrcsDir).walkEachFile().none()) {
          featuresToRemove += feature
        }
      }

      feature.generatedSourcesDir?.let { generatedSrcsDir ->
        if (projectDir.resolve(generatedSrcsDir).walkEachFile().none()) {
          featuresToRemove += feature
        }
      }

      if (feature.matchingText.isNotEmpty()) {
        if (!feature.hasMatchingTextIn(srcsDir)) {
          featuresToRemove += feature
        }
      }

      val isRemoving = featuresToRemove.size != initialRemoveSize
      if (isRemoving) {
        feature.replacementPatterns
          .takeUnless { it.isEmpty() }
          ?.let { replacementPatterns ->
            for ((replacementPattern, replacement) in replacementPatterns) {
              buildFileText =
                buildFileText.replace(replacementPattern, replacement).removeEmptyBraces()
            }
          }
      }
    }

    JsonTools.toJson<Set<ModuleFeature>>(
      featuresToRemoveOutputFile,
      featuresToRemove.toSortedSet(compareBy { it.name }),
    )

    val hasBuildFileChanges = initialBuildFileHash != buildFileText.hashCode()
    val shouldAutoFix = autoFix.getOrElse(false)
    if (hasBuildFileChanges) {
      if (shouldAutoFix) {
        buildFile.writeText(buildFileText)
      } else {
        modifiedBuildFile.asFile.get().writeText(buildFileText)
      }
    }

    val allAutoFixed = featuresToRemove.all { it.replacementPatterns.isNotEmpty() }
    if (featuresToRemove.isNotEmpty()) {
      val solution = buildString {
        appendLine("The following features appear unused and can be removed:")
        appendLine()
        var first = true
        featuresToRemove.forEach {
          if (first) {
            first = false
          } else {
            appendLine()
          }
          appendLine("    ${it.name}:")
          appendLine("        ${it.explanation}")
          appendLine("        Advice: ${it.advice}")
        }
        appendLine()
        appendLine("Full list written to ${featuresToRemoveOutputFile.asFile.get().absolutePath}")
      }
      if (shouldAutoFix) {
        if (allAutoFixed) {
          logger.lifecycle("All issues auto-fixed")
        } else {
          report(
            buildFile,
            solution,
            GradleException("Not all issues could be fixed automatically"),
          )
        }
      } else {
        report(buildFile, solution)
      }
    }
  }

  private fun report(
    buildFile: Path,
    solution: String,
    exception: GradleException = GradleException(),
  ) {
    val problemId =
      ProblemId.create(
        "module-topography-validation",
        "Module validation failed!",
        FoundryShared.PROBLEM_GROUP,
      )
    problemReporter.throwing(exception, problemId) {
      fileLocation(buildFile.relativeTo(rootDirProperty.asFile.get().toPath()).toString())
      solution(solution)
      severity(Severity.ERROR)
    }
  }

  @OptIn(ExperimentalPathApi::class)
  private fun ModuleFeature.hasMatchingTextIn(srcsDir: Path): Boolean {
    logger.debug("Checking for $name annotation usages in sources")
    return srcsDir
      .walkEachFile()
      .run {
        if (matchingTextFileExtensions.isNotEmpty()) {
          filter { it.extension in matchingTextFileExtensions }
        } else {
          this
        }
      }
      .any { file ->
        file.useLines { lines ->
          for (line in lines) {
            if (matchingText.any { it in line }) {
              return@any true
            }
          }
        }
        false
      }
  }

  internal companion object {
    private const val LOG = "[ValidateModuleTopography]"
    private const val NAME = "validateModuleTopography"
    private val CI_NAME = "ci${NAME.capitalizeUS()}"
    internal val GLOBAL_CI_NAME = "global${CI_NAME.capitalizeUS()}"

    fun register(
      project: Project,
      topographyTask: TaskProvider<ModuleTopographyTask>,
      foundryProperties: FoundryProperties,
      affectedProjects: Set<String>?,
    ) {
      val publisher =
        if (affectedProjects == null || project.path in affectedProjects) {
          Publisher.interProjectPublisher(project, FoundryArtifact.SkippyValidateTopography)
        } else {
          val log = "$LOG Skipping ${project.path}:$CI_NAME because it is not affected."
          if (foundryProperties.debug) {
            project.logger.lifecycle(log)
          } else {
            project.logger.debug(log)
          }
          SkippyArtifacts.publishSkippedTask(project, NAME)
          null
        }

      val validateModuleTopographyTask =
        project.tasks.register<ValidateModuleTopographyTask>(NAME) {
          topographyJson.set(topographyTask.flatMap { it.topographyOutputFile })
          featuresConfigFile.convention(foundryProperties.topographyFeaturesConfig)
          projectDirProperty.set(project.layout.projectDirectory)
          rootDirProperty.set(project.rootDir)
          autoFix.convention(foundryProperties.topographyAutoFix)
          featuresToRemoveOutputFile.setDisallowChanges(
            project.layout.buildDirectory.file("foundry/topography/validate/featuresToRemove.json")
          )
          modifiedBuildFile.setDisallowChanges(
            project.layout.buildDirectory.file(
              "foundry/topography/validate/modified-build.gradle.kts"
            )
          )
        }
      val ciValidateModuleTopographyTask =
        SimpleFileProducerTask.registerOrConfigure(
          project,
          CI_NAME,
          description = "Lifecycle task to run $NAME for ${project.path}.",
          group = LifecycleBasePlugin.VERIFICATION_GROUP,
        ) {
          dependsOn(validateModuleTopographyTask)
        }
      publisher?.publish(ciValidateModuleTopographyTask)
    }
  }
}

private val EMPTY_DSL_BLOCK = "(\\w+\\s*\\{\\s*\\})+".toRegex()

/**
 * Removes redundant empty braces from the string. The method iteratively replaces patterns of empty
 * DSL blocks until no such patterns remain and trims the trailing whitespace.
 *
 * Example:
 * ```
 * val input = "block1 { } block2 { content }"
 * val result = input.removeEmptyBraces()
 * println(result) // Output: "block2 { content }"
 * ```
 *
 * @return A new string with all occurrences of empty braces removed.
 */
internal fun String.removeEmptyBraces(): String {
  var result = this
  while (EMPTY_DSL_BLOCK.containsMatchIn(result)) {
    result = EMPTY_DSL_BLOCK.replace(result, "").trimEnd()
  }
  return result
}
