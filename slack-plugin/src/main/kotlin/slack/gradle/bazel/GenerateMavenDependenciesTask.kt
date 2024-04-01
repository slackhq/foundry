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
package slack.gradle.bazel

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import slack.gradle.findByType
import slack.gradle.register

@UntrackedTask(because = "This is an on-demand task")
public abstract class GenerateMavenDependenciesTask
@Inject
constructor(layout: ProjectLayout, objects: ObjectFactory) : DefaultTask() {

  @get:Input public abstract val mavenArtifacts: MapProperty<String, String>
  @get:Input public abstract val forcedMavenArtifacts: ListProperty<String>
  @get:Input public abstract val excludedKeys: SetProperty<String>

  /**
   * List of module identifiers to extra suffixes we should include. This is useful for KMP
   * artifacts
   */
  @get:Input public abstract val suffixMappings: MapProperty<String, String>

  @get:OutputFile
  public val outputFile: RegularFileProperty =
    objects
      .fileProperty()
      .convention(layout.projectDirectory.dir("third_party").file("maven_dependencies.bzl"))

  init {
    group = "bazel"
    description = "Generate maven dependencies for Bazel"
  }

  @TaskAction
  public fun generate() {
    /*
    MAVEN_ARTIFACTS = [
        "com.google.code.findbugs:jsr305:3.0.2",
        "androidx.annotation:annotation:1.8.0-alpha01",
        "androidx.compose.ui:ui-tooling-preview:1.6.2",
        "androidx.fragment:fragment-ktx:1.7.0-alpha10",
        "androidx.recyclerview:recyclerview:1.4.0-alpha01",
        "com.squareup.okhttp3:okhttp:5.0.0-alpha.12",
        "junit:junit:4.13.2",
        "com.google.truth:truth:1.4.1",
        "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0",
        "org.jetbrains.kotlin:kotlin-test:1.9.22",
        "io.reactivex.rxjava3:rxjava:3.1.8",
        "org.jetbrains.kotlin:kotlin-parcelize-runtime:1.9.22",
        "app.cash.sqldelight:runtime-jvm:2.0.1",
    ]

    FORCED_MAVEN_ARTIFACTS = []
     */

    val suffixMappings = suffixMappings.get()
    val excludedKeys = excludedKeys.get().map { it.replace('-', '.') }
    val allArtifacts = mavenArtifacts.get()

    // Mapping of groups to versions
    val boms =
      allArtifacts
        .filterKeys { it.endsWith(".bom") }
        .values
        .associate { it.substringBefore(':') to it.substringAfterLast(':') }

    logger.lifecycle("Boms are ${boms}")

    val mavenArtifacts =
      allArtifacts
        .filterKeys { it !in excludedKeys }
        .values
        .asSequence()
        .map {
          val (group, artifact, version) = it.split(":")
          val actualVersion =
            if (version == "null") {
              boms[group].also {
                if (it == null) {
                  logger.lifecycle("Could not find bom for $group")
                } else {
                  logger.lifecycle("Found bom for $group")
                }
              }
            } else {
              version
            }
          "$group:$artifact" to actualVersion
        }
        .filterNot { it.second == null }
        // Filter out bom artifacts
        .filterNot { it.first.endsWith("-bom") }
        .flatMap { entry ->
          suffixMappings[entry.first]?.let { suffix ->
            listOf(entry, "${entry.first}$suffix" to entry.second)
          } ?: listOf(entry)
        }
        .map { "${it.first}:${it.second}" }
        .toList()

    val forcedMavenArtifacts = forcedMavenArtifacts.get()
    outputFile
      .get()
      .asFile
      .writeText(
        buildString {
          appendLine("MAVEN_ARTIFACTS = [")
          for (artifact in mavenArtifacts) {
            appendLine("    \"$artifact\",")
          }
          appendLine("]")
          appendLine()
          if (forcedMavenArtifacts.isEmpty()) {
            appendLine("FORCED_MAVEN_ARTIFACTS = []")
          } else {
            appendLine("FORCED_MAVEN_ARTIFACTS = [")
            for (artifact in forcedMavenArtifacts) {
              appendLine("    \"$artifact\",")
            }
            appendLine("]")
          }
        }
      )
  }

  internal companion object {
    fun register(project: Project) {
      val catalogExtension =
        project.extensions.findByType<VersionCatalogsExtension>()
          ?: error("Could not find any version catalogs!")

      project.tasks.register<GenerateMavenDependenciesTask>("generateBazelMavenDependencies") {
        for (name in catalogExtension.catalogNames) {
          val catalog = catalogExtension.named(name)
          for (alias in catalog.libraryAliases) {
            mavenArtifacts.put(
              alias,
              catalog.findLibrary(alias).get().map { "${it.group}:${it.name}:${it.version}" },
            )
          }
        }
      }
    }
  }
}
