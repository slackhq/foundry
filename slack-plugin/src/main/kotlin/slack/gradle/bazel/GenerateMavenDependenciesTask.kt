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

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import slack.gradle.findByType
import slack.gradle.register

@UntrackedTask(because = "This is an on-demand task")
internal abstract class GenerateMavenDependenciesTask : DefaultTask() {

  @get:Input abstract val mavenArtifacts: ListProperty<String>
  @get:Input abstract val forcedMavenArtifacts: ListProperty<String>

  @get:OutputFile abstract val outputFile: RegularFileProperty

  init {
    group = "slack"
    description = "Generate maven dependencies for Bazel"
  }

  @TaskAction
  fun generate() {
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

    val mavenArtifacts =
      mavenArtifacts
        .get()
        // Filter out bom artifacts
        .filterNot { it.contains("-bom:") }
        .sorted()
    val forcedMavenArtifacts = forcedMavenArtifacts.get()
    outputFile
      .get()
      .asFile
      .writeText(
        """
      MAVEN_ARTIFACTS = [
          ${mavenArtifacts.joinToString(",\n") { "\"$it\"" }},
      ]

      FORCED_MAVEN_ARTIFACTS = [
          ${forcedMavenArtifacts.joinToString(",\n") { "\"$it\"" }},
      ]
      """
          .trimIndent()
      )
  }

  companion object {
    fun register(project: Project) {
      val catalogExtension =
        project.extensions.findByType<VersionCatalogsExtension>()
          ?: error("Could not find any version catalogs!")

      project.tasks.register<GenerateMavenDependenciesTask>("generateBazelMavenDependencies") {
        for (name in catalogExtension.catalogNames) {
          val catalog = catalogExtension.named(name)
          for (alias in catalog.libraryAliases) {
            mavenArtifacts.add(
              catalog.findLibrary(alias).get().map { "${it.group}:${it.name}:${it.version}" }
            )
          }
        }
        outputFile.set(
          project.layout.projectDirectory.dir("third_party").file("maven_dependencies.bzl")
        )
      }
    }
  }
}
