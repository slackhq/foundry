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
import com.google.devtools.ksp.gradle.KspAATask
import com.google.devtools.ksp.gradle.KspTask
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.adapter
import foundry.cli.walkEachFile
import foundry.gradle.FoundryExtension
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
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.gradle.internal.KaptTask
import org.jetbrains.kotlin.gradle.tasks.KaptGenerateStubs

private fun Provider<Boolean>.associateWithFeature(
  feature: ModuleFeature
): Provider<Map<String, Boolean>> {
  return map { mapOf(feature.name to it) }
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

    JsonWriter.of(topographyOutputFile.asFile.get().sink().buffer()).use {
      MOSHI.adapter<ModuleTopography>().toJson(it, topography)
    }
  }

  internal companion object {
    fun register(
      project: Project,
      foundryExtension: FoundryExtension,
    ): TaskProvider<ModuleTopographyTask> {
      val task =
        project.tasks.register<ModuleTopographyTask>("moduleTopography") {
          projectName.set(project.name)
          projectPath.set(project.path)
          features.putAll(
            foundryExtension.featuresHandler.moshiHandler.moshiCodegen.associateWithFeature(
              KnownFeatures.MoshiCodeGen
            )
          )
          features.putAll(
            foundryExtension.featuresHandler.circuitHandler.codegen.associateWithFeature(
              KnownFeatures.CircuitInject
            )
          )
          features.putAll(
            foundryExtension.featuresHandler.daggerHandler.enabled.associateWithFeature(
              KnownFeatures.Dagger
            )
          )
          features.putAll(
            foundryExtension.featuresHandler.daggerHandler.useDaggerCompiler.associateWithFeature(
              KnownFeatures.DaggerCompiler
            )
          )
          features.putAll(
            foundryExtension.featuresHandler.composeHandler.enableCompiler.associateWithFeature(
              KnownFeatures.Compose
            )
          )
          features.putAll(
            foundryExtension.androidHandler.featuresHandler.androidTest.associateWithFeature(
              KnownFeatures.AndroidTest
            )
          )
          features.putAll(
            foundryExtension.androidHandler.featuresHandler.robolectric.associateWithFeature(
              KnownFeatures.Robolectric
            )
          )
          features.putAll(
            project
              .provider { foundryExtension.androidHandler.featuresHandler.viewBindingEnabled() }
              .associateWithFeature(KnownFeatures.ViewBinding)
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

      registerValidationTask(project, task)
      return task
    }

    fun registerValidationTask(
      project: Project,
      topographyTask: TaskProvider<ModuleTopographyTask>,
    ) {
      project.tasks.register<ValidateModuleTopographyTask>("validateModuleTopography") {
        topographyJson.set(topographyTask.flatMap { it.topographyOutputFile })
        featuresToRemoveOutputFile.setDisallowChanges(
          project.layout.buildDirectory.file("foundry/topography/validate/featuresToRemove.json")
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

  @get:Internal public abstract val projectDirProperty: DirectoryProperty

  @get:OutputFile public abstract val featuresToRemoveOutputFile: RegularFileProperty

  init {
    group = "foundry"
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
    var buildFileModified = false
    var buildFileText = buildFile.readText()

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
        feature.removalRegex?.let(::Regex)?.let { removalRegex ->
          buildFileModified = true
          buildFileText = buildFileText.replace(removalRegex, "").removeEmptyBraces()
        }
      }
    }

    JsonWriter.of(featuresToRemoveOutputFile.asFile.get().sink().buffer()).use {
      MOSHI.adapter<Set<ModuleFeature>>()
        .toJson(it, featuresToRemove.toSortedSet(compareBy { it.name }))
    }

    if (buildFileModified) {
      buildFile.writeText(buildFileText)
    }

    if (featuresToRemove.isNotEmpty()) {
      throw AssertionError(
        """
          Validation failed for the following features:

          ${featuresToRemove.joinToString("\n", transform = ModuleFeature::removalMessage)}

          Full list written to ${featuresToRemoveOutputFile.asFile.get().absolutePath}
        """
          .trimIndent()
      )
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
