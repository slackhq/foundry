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
package foundry.gradle.tasks

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

/** Marker interface for Foundry validation tasks that can be depended on by type. */
public interface FoundryValidationTask : Task {
  public companion object {
    internal fun registerLifecycleTask(project: Project): TaskProvider<out Task> {
      return LifecycleTask.register(project, "validateFoundryProject") {
        dependsOn(project.tasks.withType(FoundryValidationTask::class.java))
      }
    }
  }
}
