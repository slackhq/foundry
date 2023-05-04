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
package slack.gradle

import java.io.File
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.toolchain.JvmVendorSpec
import slack.gradle.tasks.detektbaseline.MergeDetektBaselinesTask
import slack.gradle.tasks.robolectric.UpdateRobolectricJarsTask

/** Registry of global configuration info. */
public class GlobalConfig
private constructor(
  internal val updateRobolectricJarsTask: TaskProvider<UpdateRobolectricJarsTask>,
  internal val mergeDetektBaselinesTask: TaskProvider<MergeDetektBaselinesTask>?,
  internal val kotlinDaemonArgs: List<String>,
  internal val errorProneCheckNamesAsErrors: List<String>,
  internal val affectedProjects: Set<String>?,
  internal val jvmVendor: JvmVendorSpec?
) {

  internal companion object {
    operator fun invoke(project: Project): GlobalConfig {
      check(project == project.rootProject) { "Project is not root project!" }
      val globalSlackProperties = SlackProperties(project)
      val robolectricJarsDownloadTask =
        project.createRobolectricJarsDownloadTask(globalSlackProperties)
      val mergeDetektBaselinesTask =
        if (
          project.gradle.startParameter.taskNames.any { it == MergeDetektBaselinesTask.TASK_NAME }
        ) {
          project.tasks.register<MergeDetektBaselinesTask>(MergeDetektBaselinesTask.TASK_NAME) {
            outputFile.set(project.layout.projectDirectory.file("config/detekt/baseline.xml"))
          }
        } else {
          null
        }
      return GlobalConfig(
        updateRobolectricJarsTask = robolectricJarsDownloadTask,
        mergeDetektBaselinesTask = mergeDetektBaselinesTask,
        kotlinDaemonArgs = globalSlackProperties.kotlinDaemonArgs.split(" "),
        errorProneCheckNamesAsErrors =
          globalSlackProperties.errorProneCheckNamesAsErrors?.split(":").orEmpty(),
        affectedProjects =
          globalSlackProperties.affectedProjects?.let { file ->
            project.logger.lifecycle("[Skippy] Affected projects found in '$file'")
            // Check file existence. This way we can allow specifying the property even if it
            // doesn't exist, which can be more convenient in CI pipelines.
            if (file.exists()) {
              file.readLines().toSet().also { loadedProjects ->
                project.logger.lifecycle("[Skippy] Loaded ${loadedProjects.size} affected projects")
              }
            } else {
              project.logger.lifecycle("[Skippy] Could not load affected projects from '$file'")
              null
            }
          },
        jvmVendor =
          globalSlackProperties.jvmVendor.map(JvmVendorSpec::matching).orNull.also {
            project.logger.lifecycle("[SGP] JVM vendor: $it")
          }
      )
    }
  }
}

private fun Project.createRobolectricJarsDownloadTask(
  slackProperties: SlackProperties
): TaskProvider<UpdateRobolectricJarsTask> {
  check(isRootProject) {
    "Robolectric jars task should only be created once on the root project. Tried to apply on $name"
  }

  return tasks.register<UpdateRobolectricJarsTask>("updateRobolectricJars") {
    val sdksProvider = providers.provider { slackProperties.robolectricTestSdks }
    val iVersionProvider = providers.provider { slackProperties.robolectricIVersion }
    sdkVersions.set(sdksProvider)
    instrumentedVersion.set(iVersionProvider)
    val gradleUserHomeDir = gradle.gradleUserHomeDir
    outputDir.set(project.layout.dir(project.provider { robolectricJars(gradleUserHomeDir) }))
    offline.set(project.gradle.startParameter.isOffline)

    // If we already have the expected jars downloaded locally, then we can mark this task as up
    // to date.
    val robolectricJarsDir = robolectricJars(gradleUserHomeDir, createDirsIfMissing = false)
    outputs.upToDateWhen {
      // Annoyingly this doesn't seem to actually seem to make the task _not_ run even if it
      // returns true because Gradle APIs make no sense.
      if (robolectricJarsDir.exists()) {
        val actual =
          UpdateRobolectricJarsTask.jarsIn(robolectricJarsDir).mapTo(LinkedHashSet(), File::getName)
        val expected = sdksProvider.get().mapTo(LinkedHashSet()) { sdkFor(it).dependencyJar().name }

        // If there's any delta here, let's re-run to be safe. Covers:
        // - New jars to download
        // - Stale old jars to delete
        actual == expected
      } else {
        false
      }
    }
    // We can't reliably cache this. This call is redundant since we don't declare output, but
    // just to be explicit.
    outputs.cacheIf { false }
  }
}
