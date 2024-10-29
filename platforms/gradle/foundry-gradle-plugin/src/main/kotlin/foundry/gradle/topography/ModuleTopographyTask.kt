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
import kotlin.io.path.useLines
import kotlin.jvm.optionals.getOrNull
import okio.buffer
import okio.sink
import okio.source
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.plugins.PluginRegistry
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
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

public abstract class ModuleTopographyTask : DefaultTask() {
  @get:Input public abstract val projectName: Property<String>

  @get:Input public abstract val projectPath: Property<String>

  @get:Input @get:Optional public abstract val moshiCodeGenEnabled: Property<Boolean>

  @get:Input @get:Optional public abstract val circuitCodeGenEnabled: Property<Boolean>

  @get:Input @get:Optional public abstract val daggerEnabled: Property<Boolean>

  @get:Input @get:Optional public abstract val daggerCompilerEnabled: Property<Boolean>

  @get:Input @get:Optional public abstract val viewBindingEnabled: Property<Boolean>

  @get:Input @get:Optional public abstract val composeEnabled: Property<Boolean>

  @get:Input @get:Optional public abstract val androidTestEnabled: Property<Boolean>

  @get:Input @get:Optional public abstract val robolectricEnabled: Property<Boolean>

  @get:Input @get:Optional public abstract val pluginsProperty: SetProperty<String>

  // TODO source DAGP files to check usage of Android APIs?

  @get:OutputFile public abstract val topographyOutputFile: RegularFileProperty

  init {
    group = "foundry"
  }

  @TaskAction
  public fun compute() {
    val featuresEnabled = mutableSetOf<ModuleFeature>()

    if (androidTestEnabled.getOrElse(false)) {
      featuresEnabled += Features.AndroidTest
    }

    if (robolectricEnabled.getOrElse(false)) {
      featuresEnabled += Features.Robolectric
    }

    val plugins = pluginsProperty.getOrElse(emptySet())

    if (viewBindingEnabled.getOrElse(false)) {
      featuresEnabled += Features.ViewBinding
    }
    if (daggerCompilerEnabled.getOrElse(false)) {
      featuresEnabled += Features.DaggerCompiler
    }
    if (composeEnabled.getOrElse(false)) {
      featuresEnabled += Features.Compose
    }
    if (daggerEnabled.getOrElse(false)) {
      featuresEnabled += Features.Dagger
    }
    if (moshiCodeGenEnabled.getOrElse(false)) {
      featuresEnabled += Features.MoshiCodeGen
    }
    if (circuitCodeGenEnabled.getOrElse(false)) {
      featuresEnabled += Features.CircuitInject
    }

    val topography =
      ModuleTopography(
        name = projectName.get(),
        gradlePath = projectPath.get(),
        features = featuresEnabled.toSortedSet(compareBy { it.name }),
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
      val viewBindingEnabled =
        project.provider { foundryExtension.androidHandler.featuresHandler.viewBindingEnabled() }
      val task =
        project.tasks.register<ModuleTopographyTask>("moduleTopography") {
          projectName.set(project.name)
          projectPath.set(project.path)
          moshiCodeGenEnabled.setDisallowChanges(
            foundryExtension.featuresHandler.moshiHandler.moshiCodegen
          )
          circuitCodeGenEnabled.setDisallowChanges(
            foundryExtension.featuresHandler.circuitHandler.codegen
          )
          daggerEnabled.setDisallowChanges(foundryExtension.featuresHandler.daggerHandler.enabled)
          daggerCompilerEnabled.setDisallowChanges(
            foundryExtension.featuresHandler.daggerHandler.useDaggerCompiler
          )
          composeEnabled.setDisallowChanges(foundryExtension.featuresHandler.composeHandler.enabled)
          androidTestEnabled.setDisallowChanges(
            foundryExtension.androidHandler.featuresHandler.androidTest
          )
          robolectricEnabled.setDisallowChanges(
            foundryExtension.androidHandler.featuresHandler.robolectric
          )
          this.viewBindingEnabled.setDisallowChanges(viewBindingEnabled)
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

  @get:Optional
  @get:Option(option = "validate-all", description = "Validates all")
  @get:Input
  public abstract val validateAll: Property<Boolean>

  @get:Optional
  @get:Option(option = "validate", description = "Enables validation")
  @get:Input
  public abstract val validate: Property<String>

  @get:OutputFile public abstract val featuresToRemoveOutputFile: RegularFileProperty

  init {
    group = "foundry"
  }

  @OptIn(ExperimentalPathApi::class)
  @TaskAction
  public fun compute() {
    val topography =
      topographyJson.get().asFile.source().buffer().use {
        MOSHI.adapter<ModuleTopography>().fromJson(it)
      }!!
    val features = topography.features
    val featuresToRemove = mutableSetOf<ModuleFeature>()

    val projectDir = projectDirProperty.asFile.get().toPath()
    val srcsDir = projectDir.resolve("src")

    if (Features.AndroidTest in features) {
      if (srcsDir.resolve("androidTest").walkEachFile().none()) {
        featuresToRemove += Features.AndroidTest
      }
    }

    if (Features.Robolectric in features) {
      if (srcsDir.resolve("test").walkEachFile().none()) {
        featuresToRemove += Features.Robolectric
      }
    }

    val buildDir = projectDir.resolve("build")
    val generatedSourcesDir = buildDir.resolve("generated")

    val plugins = topography.plugins
    val kspEnabled = "dev.google.devtools.ksp" in plugins
    val kaptEnabled = "org.jetbrains.kotlin.kapt" in plugins
    val parcelizeEnabled = "org.jetbrains.kotlin.plugin.parcelize" in plugins

    if (kspEnabled) {
      if (generatedSourcesDir.resolve("ksp").walkEachFile().none()) {
        featuresToRemove += Features.Ksp
      }
    }
    if (kaptEnabled) {
      if (generatedSourcesDir.resolve("source/kapt").walkEachFile().none()) {
        featuresToRemove += Features.Kapt
      }
    }
    if (Features.ViewBinding in features) {
      if (projectDir.resolve(Features.ViewBinding.generatedSourcesDir!!).walkEachFile().none()) {
        featuresToRemove += Features.ViewBinding
      }
    }
    if (Features.DaggerCompiler in features) {
      if (!Features.DaggerCompiler.hasAnnotationsUsedIn(srcsDir)) {
        featuresToRemove += Features.DaggerCompiler
      }
      if (generatedSourcesDir.resolve("source/kapt").walkEachFile().none()) {
        featuresToRemove += Features.DaggerCompiler
      }
    }

    if (Features.Compose in features) {
      if (!Features.Compose.hasAnnotationsUsedIn(srcsDir)) {
        featuresToRemove += Features.Compose
      }
    }
    if (Features.Dagger in features) {
      if (!Features.Dagger.hasAnnotationsUsedIn(srcsDir)) {
        featuresToRemove += Features.Dagger
      }
    }
    if (Features.MoshiCodeGen in features) {
      if (!Features.MoshiCodeGen.hasAnnotationsUsedIn(srcsDir)) {
        featuresToRemove += Features.MoshiCodeGen
      }
    }
    if (Features.CircuitInject in features) {
      if (!Features.CircuitInject.hasAnnotationsUsedIn(srcsDir)) {
        featuresToRemove += Features.CircuitInject
      }
    }
    if (parcelizeEnabled) {
      if (!Features.Parcelize.hasAnnotationsUsedIn(srcsDir)) {
        featuresToRemove += Features.Parcelize
      }
    }

    JsonWriter.of(featuresToRemoveOutputFile.asFile.get().sink().buffer()).use {
      MOSHI.adapter<Set<ModuleFeature>>()
        .toJson(it, featuresToRemove.toSortedSet(compareBy { it.name }))
    }

    if (featuresToRemove.isNotEmpty()) {
      logger.error(
        """
          ${featuresToRemove.joinToString("\n"){ "- ${it.removalMessage}" }}

          Validation errors written to ${featuresToRemoveOutputFile.asFile.get().absolutePath}
        """
          .trimIndent()
      )
    }

    val featuresToValidate =
      if (validateAll.getOrElse(false)) {
        featuresToRemove
      } else if (validate.orNull != null) {
        val toValidate = validate.get()
        featuresToRemove.filter { it.name == toValidate }
      } else {
        emptyList()
      }
    if (featuresToValidate.isNotEmpty()) {
      error(
        """
          Validation failed for the following features:

          ${featuresToValidate.joinToString("\n", transform = ModuleFeature::removalMessage)}

          Full list written to ${featuresToRemoveOutputFile.asFile.get().absolutePath}
        """
          .trimIndent()
      )
    }
  }

  @OptIn(ExperimentalPathApi::class)
  private fun ModuleFeature.hasAnnotationsUsedIn(srcsDir: Path): Boolean {
    logger.debug("Checking for $name annotation usages in sources")
    return srcsDir
      .walkEachFile()
      .run {
        if (matchingAnnotationsExtensions.isNotEmpty()) {
          filter { it.extension in Features.Compose.matchingAnnotationsExtensions }
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
