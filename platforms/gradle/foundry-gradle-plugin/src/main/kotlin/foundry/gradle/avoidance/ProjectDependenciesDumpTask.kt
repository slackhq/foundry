/*
 * Copyright (C) 2025 Slack Technologies, LLC
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

import foundry.common.flatMapToSet
import foundry.gradle.FoundryShared
import foundry.gradle.register
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider

/** Task to dump the set of direct project dependencies to an [outputFile]. */
@CacheableTask
public abstract class ProjectDependenciesDumpTask : DefaultTask() {

  @get:Input public abstract val projectPaths: SetProperty<String>

  @get:OutputFile public abstract val outputFile: RegularFileProperty

  init {
    group = FoundryShared.FOUNDRY_TASK_GROUP
    description = "Dumps project dependencies to an output file."
  }

  @TaskAction
  internal fun dump() {
    outputFile.get().asFile.writeText(projectPaths.get().sorted().joinToString("\n"))
  }

  public companion object {
    public const val NAME: String = "dumpProjectDependencies"

    public fun register(project: Project): TaskProvider<ProjectDependenciesDumpTask> =
      project.tasks.register<ProjectDependenciesDumpTask>(NAME) {
        projectPaths.addAll(project.directProjectDependencies)
        outputFile.set(project.layout.projectDirectory.file("gradle/implicit-projects.txt"))
      }

    private val Project.directProjectDependencies: Provider<Set<String>>
      get() {
        return provider {
          configurations
            .filter { c -> !c.isCanBeResolved }
            .flatMapToSet { c ->
              c.dependencies.filterIsInstance<ProjectDependency>().map { it.path }
            }
        }
      }
  }
}
