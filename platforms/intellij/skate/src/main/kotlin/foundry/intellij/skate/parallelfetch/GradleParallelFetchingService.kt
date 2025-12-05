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
package foundry.intellij.skate.parallelfetch

import androidx.annotation.VisibleForTesting
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import foundry.intellij.skate.SkatePluginSettings
import javax.inject.Inject
import org.jetbrains.plugins.gradle.settings.GradleSettings

@Service(Service.Level.PROJECT)
class GradleParallelFetchingService @Inject constructor(private val project: Project) {

  fun checkParallelFetching() {
    val settings = project.service<SkatePluginSettings>()

    // Check if the feature is enabled in settings
    if (!settings.isParallelFetchingReminderEnabled) {
      return
    }

    // Only run this check in Android Studio
    if (!isAndroidStudio()) {
      return
    }

    // Check if parallel model fetching is already enabled
    try {
      val gradleSettings = GradleSettings.getInstance(project)
      if (gradleSettings.isParallelModelFetch) {
        return
      }

      // Show notification to enable parallel fetching
      showEnableParallelFetchingNotification()
    } catch (e: Exception) {
      // Silently fail if the API is not available
      // This can happen if the Gradle plugin API changes or is not available
    }
  }

  @VisibleForTesting
  internal fun isAndroidStudio(): Boolean {
    val appInfo = ApplicationInfo.getInstance()
    return appInfo.fullApplicationName.contains("Android Studio", ignoreCase = true)
  }

  private fun showEnableParallelFetchingNotification() {
    val notification =
      NotificationGroupManager.getInstance()
        .getNotificationGroup("SkateGradleParallelFetching")
        .createNotification(
          "Enable Parallel Gradle Model Fetching",
          """
            Parallel Gradle model fetching can significantly improve sync times in Android Studio.

            This feature is available in Gradle 7.4+ and is recommended for faster syncs.
          """
            .trimIndent(),
          NotificationType.WARNING,
        )
        .addAction(
          object : NotificationAction("Enable Now") {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
              val gradleSettings = GradleSettings.getInstance(project)
              gradleSettings.isParallelModelFetch = true
              ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, "Gradle")
            }
          }
        )
        .addAction(
          object : NotificationAction("Open Settings") {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
              ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, "Gradle")
            }
          }
        )
        .addAction(
          object : NotificationAction("Don't Show Again") {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
              val settings = project.service<SkatePluginSettings>()
              settings.isParallelFetchingReminderEnabled = false
              notification.expire()
            }
          }
        )

    notification.notify(project)
  }
}