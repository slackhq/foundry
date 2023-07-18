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
package com.slack.sgp.intellij.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.slack.sgp.intellij.ChangelogJournal
import com.slack.sgp.intellij.ChangelogParser
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import org.intellij.lang.annotations.Language
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
    markdownFileString: String,
    parentDisposable: Disposable
  ) {

    val toolWindowContent = WhatsNewPanelContent(project, markdownFileString, parentDisposable)
    val content =
      ContentFactory.getInstance().createContent(toolWindowContent.contentPanel, "", false)
    toolWindow.contentManager.addContent(content)
    toolWindow.contentManager.addContentManagerListener(
      object : ContentManagerListener {
        override fun contentRemoved(event: ContentManagerEvent) {
          if (event.content.component == toolWindowContent.contentPanel) {
            Disposer.dispose(parentDisposable)
            toolWindow.contentManager.removeContentManagerListener(this)
          }
        }
      }
    )
  }

  private class WhatsNewPanelContent(
    project: Project,
    @Language("Markdown") markdownFileString: String,
    parentDisposable: Disposable
  ) {

    // Actual panel box for What's New at Slack
    val contentPanel: JPanel =
      JPanel().apply {
        layout = BorderLayout(0, 20)
        add(createWhatsNewPanel(project, markdownFileString, parentDisposable), BorderLayout.CENTER)
      }

    // Control Panel that takes in the current project, markdown string, and a Disposable.
    private fun createWhatsNewPanel(
      project: Project,
      @Language("Markdown") markdownFileString: String,
      parentDisposable: Disposable
    ): JComponent {
      // to take in the parsed Changelog:
      val changelogJournal = project.service<ChangelogJournal>()

      val parsedChangelog =
        ChangelogParser.readFile(markdownFileString, changelogJournal.lastReadDate)

      changelogJournal.lastReadDate = parsedChangelog.lastReadDate

      // then, pass in parsedChangelog instead of markdownFileString
      val file = LightVirtualFile("changelog.md", parsedChangelog.changeLogString ?: "")

      val panel = MarkdownJCEFHtmlPanel(project, file)
      Disposer.register(parentDisposable, panel)

      // TODO: if it's empty, don't show the panel
      val html = runReadAction {
        MarkdownUtil.generateMarkdownHtml(file, parsedChangelog.changeLogString ?: "", project)
      }

      panel.setHtml(html, 0)
      return panel.component
    }
  }
}
