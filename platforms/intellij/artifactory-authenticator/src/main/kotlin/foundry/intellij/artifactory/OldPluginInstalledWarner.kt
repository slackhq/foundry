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
package foundry.intellij.artifactory

import com.intellij.ide.plugins.PluginManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class OldPluginInstalledWarner : ProjectActivity, DumbAware {
  companion object {
    private const val GROUP_DISPLAY_ID = "Artifactory Authenticator Notifications"
  }

  override suspend fun execute(project: Project) {
    if (
      PluginManager.getPlugins().any { it.pluginId.idString == "org.jetbrains.kotlin.test.helper" }
    ) {
      val message =
        """"
            |The old "Artifactory Authenticator" is still installed.
            |Please uninstall the old one (ID is 'com.slack.intellij.artifactory', version is <1.0.0).
            |"""
          .trimMargin()

      NotificationGroupManager.getInstance()
        .getNotificationGroup(GROUP_DISPLAY_ID)
        .createNotification(message, NotificationType.WARNING)
        .notify(project)
    }
  }
}
