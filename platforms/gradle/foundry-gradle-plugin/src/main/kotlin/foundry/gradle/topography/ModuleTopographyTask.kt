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

import foundry.gradle.FoundryExtension
import foundry.gradle.FoundryProperties
import foundry.gradle.artifacts.FoundryArtifact
import foundry.gradle.artifacts.Resolver
import foundry.gradle.properties.setDisallowChanges
import foundry.gradle.register
import foundry.gradle.serviceOf
import foundry.gradle.tasks.SimpleFilesConsumerTask
import foundry.gradle.tasks.mustRunAfterSourceGeneratingTasks
import kotlin.jvm.optionals.getOrNull
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.plugins.PluginRegistry
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider

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
          DefaultFeatures.MoshiCodeGen,
          foundryExtension.featuresHandler.moshiHandler.moshiCodegen,
        )
        features.put(
          DefaultFeatures.CircuitInject,
          foundryExtension.featuresHandler.circuitHandler.codegen,
        )
        features.put(DefaultFeatures.Dagger, foundryExtension.featuresHandler.daggerHandler.enabled)
        features.put(
          DefaultFeatures.DaggerCompiler,
          foundryExtension.featuresHandler.daggerHandler.useDaggerCompiler,
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
    task.mustRunAfterSourceGeneratingTasks(project, includeCompilerTasks = false)

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
