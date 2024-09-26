/*
 * Copyright (C) 2022 Slack Technologies, LLC
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
package foundry.gradle.tasks

import foundry.gradle.artifacts.Resolver
import foundry.gradle.artifacts.SgpArtifact
import foundry.gradle.register
import foundry.gradle.util.setDisallowChanges
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.walk
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider

/**
 * A task that aggregates all the androidTest apk paths and writes them (newline-delimited) to an
 * [outputFile] in the format that Fladle expects.
 *
 * Not cacheable because this outputs absolute paths.
 */
public abstract class AndroidTestApksTask : DefaultTask() {
  @get:PathSensitive(RELATIVE)
  @get:InputFiles
  public abstract val androidTestApkDirs: ConfigurableFileCollection

  @get:OutputFile public abstract val outputFile: RegularFileProperty

  init {
    group = "slack"
  }

  @OptIn(ExperimentalPathApi::class)
  @TaskAction
  public fun writeFiles() {
    outputFile.asFile
      .get()
      .writeText(
        androidTestApkDirs
          .asSequence()
          .flatMap { it.toPath().walk() }
          .filter { it.isRegularFile() && it.extension == "apk" }
          .joinToString("\n") { apk -> "- test: ${apk.absolutePathString()}" }
      )
  }

  public companion object {
    public const val NAME: String = "aggregateAndroidTestApks"

    internal fun register(project: Project): TaskProvider<AndroidTestApksTask> {
      val resolver = Resolver.interProjectResolver(project, SgpArtifact.ANDROID_TEST_APK_DIRS)
      return project.tasks.register<AndroidTestApksTask>(NAME) {
        androidTestApkDirs.from(resolver.artifactView())
        outputFile.setDisallowChanges(
          project.layout.buildDirectory.file("slack/androidTestAggregator/aggregatedTestApks.txt")
        )
      }
    }
  }
}
