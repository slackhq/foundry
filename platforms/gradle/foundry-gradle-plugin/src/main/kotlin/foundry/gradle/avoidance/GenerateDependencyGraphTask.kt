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
package foundry.gradle.avoidance

import com.jraska.module.graph.DependencyGraph
import com.jraska.module.graph.assertion.GradleDependencyGraphFactory
import foundry.gradle.FoundryProperties
import foundry.gradle.properties.setDisallowChanges
import foundry.gradle.register
import java.io.ObjectOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider

/** A simple task that writes a serialized [dependencyGraph] to an [outputFile]. */
@CacheableTask
internal abstract class GenerateDependencyGraphTask : DefaultTask() {

  @get:Input abstract val dependencyGraph: Property<DependencyGraph.SerializableGraph>

  @get:OutputFile abstract val outputFile: RegularFileProperty

  @TaskAction
  fun generate() {
    ObjectOutputStream(outputFile.asFile.get().outputStream()).use {
      it.writeObject(dependencyGraph.get())
    }
  }

  companion object {
    private const val NAME = "generateDependencyGraph"
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
      foundryProperties: FoundryProperties,
    ): TaskProvider<GenerateDependencyGraphTask> {
      val configurationsToLook by lazy {
        val providedConfigs = foundryProperties.affectedProjectConfigurations
        providedConfigs?.splitToSequence(',')?.toSet()?.let { providedConfigSet ->
          if (foundryProperties.buildUponDefaultAffectedProjectConfigurations) {
            DEFAULT_CONFIGURATIONS + providedConfigSet
          } else {
            providedConfigSet
          }
        } ?: DEFAULT_CONFIGURATIONS
      }

      val lazyGraph by lazy {
        GradleDependencyGraphFactory.create(rootProject, configurationsToLook).serializableGraph()
      }

      return rootProject.tasks.register<GenerateDependencyGraphTask>(NAME) {
        dependencyGraph.setDisallowChanges(rootProject.provider { lazyGraph })
        outputFile.setDisallowChanges(
          rootProject.layout.buildDirectory.file("foundry/dependencyGraph/serializedGraph.bin")
        )
      }
    }
  }
}
