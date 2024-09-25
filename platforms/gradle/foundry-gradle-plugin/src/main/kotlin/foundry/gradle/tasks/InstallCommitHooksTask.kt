/*
 * Copyright (C) 2023 Slack Technologies, LLC
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

import com.google.common.io.Resources
import foundry.gradle.setProperty
import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.UntrackedTask

@UntrackedTask(because = "This is run on-demand")
public abstract class InstallCommitHooksTask
@Inject
constructor(layout: ProjectLayout, objects: ObjectFactory) : DefaultTask() {
  @get:Input
  public val names: SetProperty<String> =
    objects
      .setProperty<String>()
      .convention(setOf("post-checkout", "post-commit", "post-merge", "pre-commit", "pre-push"))

  @get:OutputDirectory
  public val outputHooksDir: DirectoryProperty =
    objects.directoryProperty().convention(layout.projectDirectory.dir("config/git/hooks"))

  init {
    group = "slack"
    description = "Installs basic git hook files for formatting to a given output directory."
  }

  @TaskAction
  public fun install() {
    val hookFiles =
      names.get().associateWith { name -> Resources.getResource("githook-$name").readText() }

    val outputDir = outputHooksDir.asFile.get()
    logger.lifecycle("Writing git hooks to $outputDir")
    outputDir.mkdirs()
    for ((name, text) in hookFiles) {
      logger.lifecycle("Writing $name")
      File(outputDir, name).writeText(text)
    }
    logger.lifecycle(
      "Finished! Be sure to point git at the hooks location: \"git config core.hooksPath $outputDir\""
    )
  }

  internal companion object {
    private const val NAME = "installCommitHooks"

    fun register(rootProject: Project): TaskProvider<InstallCommitHooksTask> {
      // No idea why the reified register we have doesn't work here
      return rootProject.tasks.register(NAME, InstallCommitHooksTask::class.java)
    }
  }
}
