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
package slack.gradle.avoidance

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import slack.gradle.artifacts.Resolver
import slack.gradle.artifacts.SgpArtifact
import slack.gradle.register
import slack.gradle.util.setDisallowChanges

/**
 * A simple task that writes a newline-delimited list of project paths that produce androidTest
 * APKs.
 *
 * @see androidTestProjectInputs for more details on how this is computed.
 */
@CacheableTask
internal abstract class GenerateAndroidTestProjectPathsTask : DefaultTask() {
  /**
   * Consumed artifacts of project paths that produce androidTest APKs. Each file will just have one
   * line that contains a project path.
   */
  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:InputFiles
  abstract val androidTestProjectInputs: ConfigurableFileCollection

  @get:OutputFile abstract val outputFile: RegularFileProperty

  @TaskAction
  fun mergePaths() {
    val androidTestProjects =
      androidTestProjectInputs.map { it.readText().trim() }.distinct().sorted()
    outputFile.get().asFile.writeText(androidTestProjects.joinToString("\n"))
  }

  companion object {
    private const val NAME = "generateAndroidTestProjectPaths"

    fun register(rootProject: Project): TaskProvider<GenerateAndroidTestProjectPathsTask> {
      val androidTestApksResolver =
        Resolver.interProjectResolver(rootProject, SgpArtifact.SKIPPY_ANDROID_TEST_PROJECT)
      return rootProject.tasks.register<GenerateAndroidTestProjectPathsTask>(NAME) {
        androidTestProjectInputs.from(androidTestApksResolver.artifactView())
        outputFile.setDisallowChanges(
          rootProject.layout.buildDirectory.file("slack/androidTestProjectPaths/paths.txt")
        )
      }
    }
  }
}
