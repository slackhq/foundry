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
package foundry.gradle.tasks

import foundry.gradle.artifacts.Publisher
import foundry.gradle.registerOrConfigure
import java.io.File
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider

@CacheableTask
internal abstract class SimpleFileProducerTask : DefaultTask() {
  @get:Input abstract val input: Property<String>

  @get:OutputFile abstract val output: RegularFileProperty

  @TaskAction
  fun writeText() {
    val outputFile = output.get().asFile
    outputFile.writeText(input.get())
  }

  companion object {
    fun registerOrConfigure(
      project: Project,
      name: String,
      description: String,
      outputFilePath: String = "artifactMetadata/$name/produced.txt",
      input: String = "${project.path}:$name",
      group: String = "slack",
      action: Action<SimpleFileProducerTask> = Action {},
    ): TaskProvider<SimpleFileProducerTask> {
      return project.tasks.registerOrConfigure<SimpleFileProducerTask>(name) {
        this.group = group
        this.description = description
        this.input.set(input)
        output.set(project.layout.buildDirectory.file(outputFilePath))
        action.execute(this)
      }
    }
  }
}

@CacheableTask
internal abstract class SimpleFilesConsumerTask : DefaultTask() {
  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:InputFiles
  abstract val inputFiles: ConfigurableFileCollection

  @get:OutputFile abstract val output: RegularFileProperty

  @TaskAction
  fun mergeFiles() {
    val outputFile = output.get().asFile
    outputFile.writeText(
      inputFiles.files
        .map {
          logger.debug("Merging file: $it")
          it.readText()
        }
        .sorted()
        .joinToString("\n")
    )
  }

  companion object {
    fun registerOrConfigure(
      project: Project,
      name: String,
      description: String,
      inputFiles: Provider<Set<File>>,
      outputFilePath: String = "artifactMetadata/$name/resolved.txt",
      group: String = "slack",
      action: Action<SimpleFilesConsumerTask> = Action {},
    ): TaskProvider<SimpleFilesConsumerTask> {
      return project.tasks.registerOrConfigure<SimpleFilesConsumerTask>(name) {
        this.group = group
        this.description = description
        this.inputFiles.from(inputFiles)
        output.set(project.layout.buildDirectory.file(outputFilePath))
        action.execute(this)
      }
    }
  }
}

internal fun Publisher<*>.publish(provider: TaskProvider<SimpleFileProducerTask>) {
  publish(provider.flatMap { it.output })
}

/** Inverse of the above [Publisher.publish] available for fluent calls. */
internal fun TaskProvider<SimpleFileProducerTask>.publishWith(publisher: Publisher<*>) {
  publisher.publish(this)
}
