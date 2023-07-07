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
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import org.intellij.lang.annotations.Language
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil
import org.intellij.plugins.markdown.ui.preview.jcef.MarkdownJCEFHtmlPanel

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
        add(createControlsPanel(project, markdownFileString, parentDisposable), BorderLayout.CENTER)
      }

    private fun createControlsPanel(
      project: Project,
      @Language("Markdown") markdownFileString: String,
      parentDisposable: Disposable
    ): JComponent {
      println(markdownFileString)
      val file = LightVirtualFile("changelog.md", markdownFileString)
      val panel = MarkdownJCEFHtmlPanel(project, file)
      Disposer.register(parentDisposable, panel)
      val html = runReadAction {
        MarkdownUtil.generateMarkdownHtml(file, markdownFileString, project)
      }

      panel.setHtml(html, 0)
      return panel.component
    }
  }
}
