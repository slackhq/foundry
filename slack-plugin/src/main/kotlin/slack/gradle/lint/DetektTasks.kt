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
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import java.io.File
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin
import slack.gradle.SlackProperties
import slack.gradle.configure
import slack.gradle.configureEach
import slack.gradle.register
import slack.gradle.tasks.DetektDownloadTask
import slack.gradle.tasks.detektbaseline.MergeDetektBaselinesTask
import slack.gradle.util.sneakyNull

internal object DetektTasks {
  private const val GLOBAL_CI_DETEKT_TASK_NAME = "globalCiDetekt"
  private const val LOG = "SlackDetekt:"

  fun configureRootProject(
    project: Project,
    slackProperties: SlackProperties,
  ) {
    // Add detekt download task
    slackProperties.versions.detekt?.let { detektVersion ->
      project.tasks.register<DetektDownloadTask>("updateDetekt") {
        version.set(detektVersion)
        outputFile.set(project.layout.projectDirectory.file("config/bin/detekt"))
      }

      project.tasks.register(GLOBAL_CI_DETEKT_TASK_NAME) {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Global lifecycle task to run all dependent detekt tasks."
      }
    }
  }

  fun configureSubProject(
    project: Project,
    slackProperties: SlackProperties,
    affectedProjects: Set<String>?,
    jvmTarget: String,
    mergeDetektBaselinesTask: TaskProvider<MergeDetektBaselinesTask>?,
  ) {
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

        slackProperties.detektBaseline?.let { baselineFile ->
          baseline =
            if (mergeDetektBaselinesTask != null) {
              project.tasks.withType(DetektCreateBaselineTask::class.java).configureEach {
                mergeDetektBaselinesTask.configure { baselineFiles.from(baseline) }
              }
              project.file("${project.buildDir}/intermediates/detekt/baseline.xml")
            } else {
              project.file(project.rootProject.file(baselineFile))
            }
        }
      }

      val globalTask =
        if (affectedProjects == null || project.path in affectedProjects) {
          project.rootProject.tasks.named(GLOBAL_CI_DETEKT_TASK_NAME)
        } else {
          val log = "$LOG Skipping ${project.path}:detekt because it is not affected."
          if (slackProperties.debug) {
            project.logger.lifecycle(log)
          } else {
            project.logger.debug(log)
          }
          null
        }

      project.tasks.configureEach<Detekt> {
        this.jvmTarget = jvmTarget
        exclude("**/build/**")
        jdkHome.set(sneakyNull<File>())

        val detektTask = this
        globalTask?.configure { dependsOn(detektTask) }
      }
    }
  }
}
