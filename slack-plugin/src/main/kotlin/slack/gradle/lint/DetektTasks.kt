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
package slack.gradle.lint

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.DetektPlugin
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.specs.Spec
import org.gradle.language.base.plugins.LifecycleBasePlugin
import slack.gradle.SlackProperties
import slack.gradle.artifacts.Publisher
import slack.gradle.artifacts.Resolver
import slack.gradle.artifacts.SgpArtifact
import slack.gradle.avoidance.SkippyArtifacts
import slack.gradle.configure
import slack.gradle.configureEach
import slack.gradle.isRootProject
import slack.gradle.register
import slack.gradle.tasks.DetektDownloadTask
import slack.gradle.tasks.SimpleFileProducerTask
import slack.gradle.tasks.SimpleFilesConsumerTask
import slack.gradle.tasks.publish
import slack.gradle.util.setDisallowChanges
import slack.gradle.util.sneakyNull

internal object DetektTasks {
  private const val GLOBAL_CI_DETEKT_TASK_NAME = "globalCiDetekt"
  private const val CI_DETEKT_TASK_NAME = "ciDetekt"
  private const val LOG = "SlackDetekt:"

  fun configureRootProject(project: Project, slackProperties: SlackProperties) {
    // Add detekt download task
    slackProperties.versions.detekt?.let { detektVersion ->
      project.tasks.register<DetektDownloadTask>("updateDetekt") {
        version.setDisallowChanges(detektVersion)
        outputFile.setDisallowChanges(project.layout.projectDirectory.file("config/bin/detekt"))
      }

      val resolver = Resolver.interProjectResolver(project, SgpArtifact.SKIPPY_DETEKT)
      SimpleFilesConsumerTask.registerOrConfigure(
        project,
        GLOBAL_CI_DETEKT_TASK_NAME,
        description = "Global lifecycle task to run all dependent detekt tasks.",
        inputFiles = resolver.artifactView(),
      )
    }
  }

  fun configureSubProject(
    project: Project,
    slackProperties: SlackProperties,
    affectedProjects: Set<String>?,
    jvmTarget: String,
  ) {
    check(!project.isRootProject) {
      "This method should only be called for subprojects, not the root project."
    }
    if (slackProperties.versions.detekt == null) return

    project.pluginManager.withPlugin("io.gitlab.arturbosch.detekt") {
      project.logger.debug("$LOG Configuring Detekt tasks for project ${project.path}...")

      // Configuration examples https://arturbosch.github.io/detekt/kotlindsl.html
      project.configure<DetektExtension> {
        buildUponDefaultConfig = true
        toolVersion =
          slackProperties.versions.detekt ?: error("missing 'detekt' version in version catalog")
        project.rootProject.file("config/detekt/detekt.yml")
        slackProperties.detektConfigs?.let { configs ->
          for (configFile in configs) {
            config.from(project.rootProject.file(configFile))
          }
        }

        // Note we need to _explicitly_ null this out if it's not present, as otherwise detekt will
        // default to using the project's "detekt-baseline.xml" file if available, which we don't
        // want.
        baseline =
          slackProperties.detektBaselineFileName?.let { baselineFile ->
            project.layout.projectDirectory.file(baselineFile).asFile
          }
      }

      val publisher =
        if (affectedProjects == null || project.path in affectedProjects) {
          Publisher.interProjectPublisher(project, SgpArtifact.SKIPPY_DETEKT)
        } else {
          val log = "$LOG Skipping ${project.path}:detekt because it is not affected."
          if (slackProperties.debug) {
            project.logger.lifecycle(log)
          } else {
            project.logger.debug(log)
          }
          SkippyArtifacts.publishSkippedTask(project, "detekt")
          null
        }

      // Duplicate configs due to https://github.com/detekt/detekt/issues/5940
      project.tasks.configureEach<Detekt> {
        this.jvmTarget = jvmTarget
        exclude("**/build/**")
        // Cannot use setDisallowChanges because this property is set without a convention in Detekt
        jdkHome.set(sneakyNull<Directory>())
      }
      project.tasks.configureEach<DetektCreateBaselineTask> {
        this.jvmTarget = jvmTarget
        exclude("**/build/**")
        // Cannot use setDisallowChanges because this property is set without a convention in Detekt
        jdkHome.set(sneakyNull<Directory>())
      }

      // Wire up to the global task
      // We use a filter on Detekt tasks because not every project actually makes one!
      val taskSpec =
        if (slackProperties.enableFullDetekt) {
          // Depend on all Detekt tasks with type resolution
          // The "detekt" task is excluded because it is a plain, non-type-resolution version
          Spec<Detekt> { it.name != DetektPlugin.DETEKT_TASK_NAME }
        } else {
          // Depend _only_ on the "detekt plain" task, which runs without type resolution
          Spec<Detekt> { it.name == DetektPlugin.DETEKT_TASK_NAME }
        }
      val matchingTasks = project.tasks.withType(Detekt::class.java).matching(taskSpec)
      val ciDetekt =
        SimpleFileProducerTask.registerOrConfigure(
          project,
          CI_DETEKT_TASK_NAME,
          description = "Lifecycle task to run detekt for ${project.path}.",
          group = LifecycleBasePlugin.VERIFICATION_GROUP,
        ) {
          dependsOn(matchingTasks)
        }
      publisher?.publish(ciDetekt)
    }
  }
}
