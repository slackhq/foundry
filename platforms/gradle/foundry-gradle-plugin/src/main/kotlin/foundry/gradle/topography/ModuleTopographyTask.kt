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

import com.github.ajalt.mordant.markdown.Markdown
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import foundry.cli.walkEachFile
import foundry.common.json.JsonTools
import foundry.gradle.FoundryExtension
import foundry.gradle.FoundryProperties
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
import foundry.gradle.tasks.mustRunAfterSourceGeneratingTasks
import foundry.gradle.tasks.publish
import foundry.gradle.util.toJson
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.io.path.useLines
import kotlin.io.path.writeText
import kotlin.jvm.optionals.getOrNull
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.plugins.PluginRegistry
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
    val resolver =
      Resolver.interProjectResolver(project, FoundryArtifact.SKIPPY_VALIDATE_TOPOGRAPHY)
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
          KnownFeatures.MoshiCodeGen,
          foundryExtension.featuresHandler.moshiHandler.moshiCodegen,
        )
        features.put(
          KnownFeatures.CircuitInject,
          foundryExtension.featuresHandler.circuitHandler.codegen,
        )
        features.put(KnownFeatures.Dagger, foundryExtension.featuresHandler.daggerHandler.enabled)
        features.put(
          KnownFeatures.DaggerCompiler,
          foundryExtension.featuresHandler.daggerHandler.useDaggerCompiler,
        )
        features.put(
          KnownFeatures.Compose,
          foundryExtension.featuresHandler.composeHandler.enableCompiler,
        )
        features.put(
          KnownFeatures.AndroidTest,
          foundryExtension.androidHandler.featuresHandler.androidTest,
        )
        features.put(
          KnownFeatures.Robolectric,
          foundryExtension.androidHandler.featuresHandler.robolectric,
        )
        features.put(
          KnownFeatures.ViewBinding,
          project.provider { foundryExtension.androidHandler.featuresHandler.viewBindingEnabled() },
        )
        topographyOutputFile.setDisallowChanges(
          project.layout.buildDirectory.file("foundry/topography/model/topography.json")
        )
      }

    // Depend on source-gen tasks
    task.mustRunAfterSourceGeneratingTasks(project)

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
    group = "foundry"
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
public abstract class ValidateModuleTopographyTask : DefaultTask() {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  public abstract val topographyJson: RegularFileProperty

  @get:Optional
  @get:Option(option = "auto-fix", description = "Enables auto-fixing build files")
  @get:Input
  public abstract val autoFix: Property<Boolean>

  @get:Internal public abstract val projectDirProperty: DirectoryProperty

  @get:OutputFile public abstract val modifiedBuildFile: RegularFileProperty
  @get:OutputFile public abstract val featuresToRemoveOutputFile: RegularFileProperty

  init {
    group = "foundry"
    @Suppress("LeakingThis")
    notCompatibleWithConfigurationCache("This task modified build files in place")
    @Suppress("LeakingThis") doNotTrackState("This task modified build files in place")
  }

  @OptIn(ExperimentalPathApi::class)
  @TaskAction
  public fun validate() {
    val topography = ModuleTopography.from(topographyJson)
    val knownFeatures = KnownFeatures.load()
    val features = buildSet {
      addAll(topography.features.map { featureKey -> knownFeatures.getValue(featureKey) })
      // Include plugin-specific features to the check here
      addAll(knownFeatures.filterValues { it.matchingPlugin in topography.plugins }.values)
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
        feature.removalPatterns?.let { removalPatterns ->
          for (removalRegex in removalPatterns) {
            buildFileText = buildFileText.replace(removalRegex, "").removeEmptyBraces()
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

    val allAutoFixed = featuresToRemove.all { !it.removalPatterns.isNullOrEmpty() }
    if (featuresToRemove.isNotEmpty()) {
      val message = buildString {
        appendLine(
          "**Validation failed! The following features appear to be unused and can be removed.**"
        )
        appendLine()
        var first = true
        featuresToRemove.forEach {
          if (first) {
            first = false
          } else {
            appendLine()
            appendLine()
          }
          appendLine("- **${it.name}:** ${it.explanation}")
          appendLine()
          appendLine("  - **Advice:** ${it.advice}")
        }
        appendLine()
        appendLine("Full list written to ${featuresToRemoveOutputFile.asFile.get().absolutePath}")
      }
      val t = Terminal(AnsiLevel.TRUECOLOR, interactive = true)
      val md = Markdown(message)
      t.println(md, stderr = true)
      if (shouldAutoFix) {
        if (allAutoFixed) {
          logger.lifecycle("All issues auto-fixed")
        } else {
          throw AssertionError("Not all issues could be fixed automatically")
        }
      } else {
        throw AssertionError()
      }
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
          Publisher.interProjectPublisher(project, FoundryArtifact.SKIPPY_VALIDATE_TOPOGRAPHY)
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
          projectDirProperty.set(project.layout.projectDirectory)
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

//// Usage
// var code = "foundry { features { compose() } }"
// code = code.replace(Regex("\\bcompose\\(\\)"), "") // remove compose()
// code = removeEmptyBraces(code) // recursively remove empty braces
//
// println(code) // Should print "<nothing>"
// TODO write tests for this
private val EMPTY_DSL_BLOCK = "(\\w*)\\s*\\{\\s*\\}".toRegex()

internal fun String.removeEmptyBraces(): String {
  var result = this
  while (EMPTY_DSL_BLOCK.containsMatchIn(result)) {
    result = EMPTY_DSL_BLOCK.replace(result, "")
  }
  return result
}
