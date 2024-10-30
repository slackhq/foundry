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

import com.android.build.gradle.internal.tasks.databinding.DataBindingGenBaseClassesTask
import com.github.ajalt.mordant.markdown.Markdown
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.google.devtools.ksp.gradle.KspAATask
import com.google.devtools.ksp.gradle.KspTask
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.adapter
import foundry.cli.walkEachFile
import foundry.gradle.FoundryExtension
import foundry.gradle.FoundryProperties
import foundry.gradle.properties.setDisallowChanges
import foundry.gradle.register
import foundry.gradle.serviceOf
import foundry.gradle.util.JsonTools.MOSHI
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.io.path.useLines
import kotlin.io.path.writeText
import kotlin.jvm.optionals.getOrNull
import okio.buffer
import okio.sink
import okio.source
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
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.gradle.internal.KaptTask
import org.jetbrains.kotlin.gradle.tasks.KaptGenerateStubs

private fun MapProperty<String, Boolean>.put(feature: ModuleFeature, provider: Provider<Boolean>) {
  put(feature.name, provider.orElse(false))
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

    JsonWriter.of(topographyOutputFile.asFile.get().sink().buffer())
      .apply { indent = "  " }
      .use { MOSHI.adapter<ModuleTopography>().toJson(it, topography) }
  }

  internal companion object {
    fun register(
      project: Project,
      foundryExtension: FoundryExtension,
      foundryProperties: FoundryProperties,
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
            project.provider {
              foundryExtension.androidHandler.featuresHandler.viewBindingEnabled()
            },
          )
          topographyOutputFile.setDisallowChanges(
            project.layout.buildDirectory.file("foundry/topography/model/topography.json")
          )

          // Depend on source-gen tasks

          // Kapt
          dependsOn(project.tasks.withType(KaptGenerateStubs::class.java))
          dependsOn(project.tasks.withType(KaptTask::class.java))
          // KSP
          dependsOn(project.tasks.withType(KspTask::class.java))
          dependsOn(project.tasks.withType(KspAATask::class.java))
          // ViewBinding
          dependsOn(project.tasks.withType(DataBindingGenBaseClassesTask::class.java))
        }

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

      registerValidationTask(project, task, foundryProperties)
      return task
    }

    fun registerValidationTask(
      project: Project,
      topographyTask: TaskProvider<ModuleTopographyTask>,
      foundryProperties: FoundryProperties,
    ) {
      project.tasks.register<ValidateModuleTopographyTask>("validateModuleTopography") {
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
    }
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
    val topography =
      topographyJson.get().asFile.source().buffer().use {
        MOSHI.adapter<ModuleTopography>().fromJson(it)
      }!!
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

    JsonWriter.of(featuresToRemoveOutputFile.asFile.get().sink().buffer())
      .apply { indent = "  " }
      .use {
        MOSHI.adapter<Set<ModuleFeature>>()
          .toJson(it, featuresToRemove.toSortedSet(compareBy { it.name }))
      }

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
