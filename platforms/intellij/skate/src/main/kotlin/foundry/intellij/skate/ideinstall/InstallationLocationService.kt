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
package foundry.intellij.skate.ideinstall

import androidx.annotation.VisibleForTesting
import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import foundry.intellij.skate.SkatePluginSettings
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths
import javax.inject.Inject

@Service(Service.Level.PROJECT)
class InstallationLocationService @Inject constructor(private val project: Project) {
  fun checkInstallationLocation() {
    val settings = project.service<SkatePluginSettings>()
    val pattern = settings.installationLocationPattern ?: return
    val infoUrl = settings.installationInfoUrl ?: return

    // print out what the current path is
    println("Running IDE from: ${PathManager.getHomePath()}")

    if (pattern.isBlank()) return

    val installationDir = PathManager.getHomePath()
    val installationPath = Paths.get(installationDir)

    if (!matchesPattern(installationPath, pattern)) {
      showWarningNotification(infoUrl)
    }
  }

  @VisibleForTesting
  internal fun matchesPattern(path: Path, pattern: String): Boolean {
    val expandedPattern = pattern.replace("\${user.home}", System.getProperty("user.home"))
    val matcher = FileSystems.getDefault().getPathMatcher("glob:$expandedPattern")
    return matcher.matches(path)
  }

  private fun showWarningNotification(infoUrl: String) {
    val installationPath = PathManager.getHomePath()

    val notification =
      NotificationGroupManager.getInstance()
        .getNotificationGroup("SkateInstallationLocation")
        .createNotification(
          "Move Android Studio for better performance",
          """
        Your IDE isn't installed under the desired path, which means you're missing out on faster Gradle syncs and significantly improved IDE performance.

        Detected path:
        `$installationPath`
        """
            .trimIndent(),
          NotificationType.WARNING,
        )
        .addAction(
          object : NotificationAction("More info") {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
              BrowserUtil.browse(infoUrl)
            }
          }
        )

    notification.notify(project)
  }
}
