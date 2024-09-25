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

import org.gradle.api.Project
import slack.gradle.artifacts.Publisher
import slack.gradle.artifacts.SgpArtifact
import slack.gradle.capitalizeUS
import slack.gradle.tasks.SimpleFileProducerTask
import slack.gradle.tasks.publishWith

internal object SkippyArtifacts {
  fun publishSkippedTask(project: Project, name: String) {
    SimpleFileProducerTask.registerOrConfigure(
        project,
        name = "skipped${name.capitalizeUS()}",
        description = "Lifecycle task to run unit tests for ${project.path} (skipped).",
      )
      .publishWith(Publisher.interProjectPublisher(project, SgpArtifact.SKIPPY_AVOIDED_TASKS))
  }
}
