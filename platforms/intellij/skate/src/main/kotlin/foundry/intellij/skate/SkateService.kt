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
package foundry.intellij.skate

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import foundry.intellij.skate.tracing.SkateTraceReporter
import foundry.intellij.skate.ui.WhatsNewPanelFactory
import java.util.function.Supplier

interface SkateProjectService {
  val traceReporter: SkateTraceReporter

  /**
   * Shows the "What's New" panel.
   *
   * @param forceShow If true, shows the panel regardless of whether there are new entries.
   */
  fun showWhatsNewPanel(forceShow: Boolean = false)
}

/**
 * This file's intended purpose is to pass in the changelog file from the file path into the What's
 * New UI of the Skate Plugin
 */
class SkateProjectServiceImpl(private val project: Project) : SkateProjectService {

  override val traceReporter: SkateTraceReporter by lazy { SkateTraceReporter(project) }

  override fun showWhatsNewPanel(forceShow: Boolean) {
    val settings = project.service<SkatePluginSettings>()
    val changelogJournal = project.service<ChangelogJournal>()

    if (!settings.isWhatsNewEnabled) return
    val projectDir = project.guessProjectDir() ?: return

    val changeLogFile = VfsUtil.findRelativeFile(projectDir, settings.whatsNewFilePath) ?: return
    val changeLogString = VfsUtil.loadText(changeLogFile)

    // Don't show the tool window if the parsed changelog is blank
    // If forceShow is true, pass null as lastReadDate to show the first section regardless of
    // whether the user has seen it before
    val lastReadDate = if (forceShow) null else changelogJournal.lastReadDate
    val parsedChangelog = ChangelogParser.readFile(changeLogString, lastReadDate)
    if (parsedChangelog.changeLogString.isNullOrBlank()) return
    // Creating the tool window
    val toolWindowManager = ToolWindowManager.getInstance(project)
    toolWindowManager.invokeLater {
      val toolWindow =
        toolWindowManager.registerToolWindow(WHATS_NEW_PANEL_ID) {
          stripeTitle = Supplier { "What's New in Slack!" }
          anchor = ToolWindowAnchor.RIGHT
        }

      WhatsNewPanelFactory().createToolWindowContent(toolWindow, project, parsedChangelog)

      toolWindow.show()
    }
  }

  companion object {
    const val WHATS_NEW_PANEL_ID = "skate-whats-new"
  }
}
