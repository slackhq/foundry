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
package foundry.intellij.skate.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.ui.jcef.JBCefApp
import foundry.intellij.compose.markdown.ui.MarkdownPanel
import foundry.intellij.skate.ChangelogJournal
import foundry.intellij.skate.ChangelogParser
import javax.swing.JComponent
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil
import org.intellij.plugins.markdown.ui.preview.jcef.MarkdownJCEFHtmlPanel

/**
 * The WhatsNewPanelFactory class takes the markdown file string from SkateService and displays it
 * in a tool window. It uses MarkdownJCEFHtmlPanel and has dependency on intellij markdown plugin to
 * properly format the markdown file and its contents
 */
class WhatsNewPanelFactory : DumbAware {

  // Function that creates the tool window
  fun createToolWindowContent(
    toolWindow: ToolWindow,
    project: Project,
    changeLogContent: ChangelogParser.PresentedChangelog,
    parentDisposable: Disposable,
  ) {
    val toolWindowContent = WhatsNewPanelContent(project, changeLogContent, parentDisposable)
    val content =
      ContentFactory.getInstance().createContent(toolWindowContent.contentPanel, "", false)
    toolWindow.contentManager.addContent(content)
    toolWindow.contentManager.addContentManagerListener(
      object : ContentManagerListener {
        override fun contentRemoved(event: ContentManagerEvent) {
          if (event.content.component == toolWindowContent.contentPanel) {
            toolWindow.contentManager.removeContentManagerListener(this)
          }
        }
      }
    )
  }

  private class WhatsNewPanelContent(
    project: Project,
    changeLogContent: ChangelogParser.PresentedChangelog,
    parentDisposable: Disposable,
  ) {
    private val logger = logger<WhatsNewPanelContent>()
    // Actual panel box for "What's New in Slack!"
    val contentPanel = createWhatsNewPanel(project, changeLogContent, parentDisposable)

    val file = LightVirtualFile("changelog.md", changeLogContent.changeLogString ?: "")

    val html = runReadAction {
      MarkdownUtil.generateMarkdownHtml(file, changeLogContent.changeLogString ?: "", project)
    }

    // Control Panel that takes in the current project, parsed string, and a Disposable.
    private fun createWhatsNewPanel(
      project: Project,
      changeLogContent: ChangelogParser.PresentedChangelog,
      parentDisposable: Disposable,
    ): JComponent {
      // to take in the parsed Changelog:
      val changelogJournal = project.service<ChangelogJournal>()

      changelogJournal.lastReadDate = changeLogContent.lastReadDate

      // We can't use JBCefApp because Studio blocks it, so instead we do this in compose.
      // https://issuetracker.google.com/issues/159933628#comment19
      val panel =
        if (JBCefApp.isSupported()) {
          logger.debug("Using JCEFHtmlPanelProvider")
          MarkdownJCEFHtmlPanel(project, file)
            .apply {
              Disposer.register(parentDisposable, this)
              setHtml(html, 0)
            }
            .component
        } else {
          MarkdownPanel.createPanel { changeLogContent.changeLogString ?: "" }
        }
      return panel
    }
  }
}
