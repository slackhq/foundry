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
package foundry.gradle.roborazzi

import foundry.gradle.FoundryProperties
import foundry.gradle.artifacts.FoundryArtifact
import foundry.gradle.artifacts.Publisher
import foundry.gradle.artifacts.Resolver
import foundry.gradle.avoidance.SkippyArtifacts
import foundry.gradle.ciUnitTestAndroidVariant
import foundry.gradle.tasks.SimpleFileProducerTask
import foundry.gradle.tasks.SimpleFilesConsumerTask
import foundry.gradle.tasks.publish
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Sets up Roborazzi tests with skippy support. Similar to how [foundry.gradle.unittest.UnitTests]
 * works.
 */
internal object RoborazziTests {
  private const val GLOBAL_CI_VERIFY_ROBORAZZI_TASK_NAME = "globalCiVerifyRoborazzi"
  private const val CI_VERIFY_ROBORAZZI_TASK_NAME = "ciVerifyRoborazzi"
  private const val VERIFY_ROBORAZZI_TASK_NAME = "verifyRoborazzi"
  private const val LOG = "FoundryRoborazziTests:"

  fun configureRootProject(project: Project) {
    val resolver = Resolver.interProjectResolver(project, FoundryArtifact.SkippyRoborazziTests)
    SimpleFilesConsumerTask.registerOrConfigure(
      project = project,
      name = GLOBAL_CI_VERIFY_ROBORAZZI_TASK_NAME,
      group = LifecycleBasePlugin.VERIFICATION_GROUP,
      description = "Global lifecycle task to run all verifyRoborazzi tasks.",
      inputFiles = resolver.artifactView(),
    )
  }

  fun configureSubproject(
    project: Project,
    foundryProperties: FoundryProperties,
    affectedProjects: Set<String>?,
    onProjectSkipped: (String, String) -> Unit,
  ) {
    foundryProperties.versions.bundles.commonRoborazzi.ifPresent {
      project.dependencies.add("testImplementation", it)
    }
    val verifyRoborazziPublisher: Publisher<FoundryArtifact>? =
      if (affectedProjects == null || project.path in affectedProjects) {
        Publisher.interProjectPublisher(project, FoundryArtifact.SkippyRoborazziTests)
      } else {
        val taskPath = "${project.path}:$VERIFY_ROBORAZZI_TASK_NAME"
        onProjectSkipped(GLOBAL_CI_VERIFY_ROBORAZZI_TASK_NAME, taskPath)
        val log = "$LOG Skipping $taskPath because it is not affected."
        if (foundryProperties.debug) {
          project.logger.lifecycle(log)
        } else {
          project.logger.debug(log)
        }
        SkippyArtifacts.publishSkippedTask(project, VERIFY_ROBORAZZI_TASK_NAME)
        null
      }

    findVerifyRoborazziTask(project, verifyRoborazziPublisher)
  }

  private fun findVerifyRoborazziTask(
    project: Project,
    roborazziPublisher: Publisher<FoundryArtifact>?,
  ) {
    val variant = project.ciUnitTestAndroidVariant()
    val variantVerifyRoborazziTaskName = "$VERIFY_ROBORAZZI_TASK_NAME$variant"
    project.logger.debug("$LOG Creating CI verifyRoborazzi tasks for variant '$variant'")
    val ciVerifyRoborazzi = registerCiVerifyRoborazzi(project, variantVerifyRoborazziTaskName)
    roborazziPublisher?.publish(ciVerifyRoborazzi)
  }

  private fun registerCiVerifyRoborazzi(
    project: Project,
    dependencyTaskName: String,
  ): TaskProvider<SimpleFileProducerTask> {
    return SimpleFileProducerTask.registerOrConfigure(
      project,
      name = CI_VERIFY_ROBORAZZI_TASK_NAME,
      group = LifecycleBasePlugin.VERIFICATION_GROUP,
      description = "Lifecycle task to run verifyRoborazzi for ${project.path}.",
    ) {
      dependsOn(dependencyTaskName)
    }
  }
}
