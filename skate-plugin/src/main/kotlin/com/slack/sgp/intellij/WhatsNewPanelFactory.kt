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
package com.slack.sgp.intellij

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.ContentFactory
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.StyleSheet

class WhatsNewPanelFactory : DumbAware {
  fun createToolWindowContent(toolWindow: ToolWindow, markdownFile: VirtualFile) {
    val toolWindowContent = WhatsNewPanelContent(toolWindow, markdownFile)
    val content =
      ContentFactory.getInstance().createContent(toolWindowContent.contentPanel, "", false)
    toolWindow.contentManager.addContent(content)
  }

  private class WhatsNewPanelContent(toolWindow: ToolWindow, markdownFile: VirtualFile) {

    val contentPanel: JPanel =
      JPanel().apply {
        layout = BorderLayout(0, 20)
        border = BorderFactory.createEmptyBorder(40, 0, 0, 0)
        add(createControlsPanel(toolWindow, markdownFile), BorderLayout.CENTER)
      }

    private fun createControlsPanel(toolWindow: ToolWindow, markdownFile: VirtualFile): JPanel {
      val options = MutableDataSet()
      val parser = Parser.builder(options).build()
      val renderer = HtmlRenderer.builder(options).build()

      val markdownContent = String(markdownFile.contentsToByteArray(), Charsets.UTF_8)
      val document = parser.parse(markdownContent)
      val htmlContent = renderer.render(document)
      println(htmlContent)
      return JPanel().apply {
        layout = BorderLayout()
        val markdownDisplay =
          JEditorPane("text/html", htmlContent).apply {
            isEditable = false
            background = Color(0x333333) // set to a dark color
            contentType = "text/html"
            editorKit = HTMLEditorKit().apply { styleSheet = styleSheet }
          }
        markdownDisplay.contentType = "text/html"

        // Enable text wrapping
        // Enable text wrapping
        val styleSheet = StyleSheet()
        styleSheet.addRule(
          "body { width: fit-content; font-family: Arial; line-height: 1.6; color: #FFF; }"
        )
        styleSheet.addRule("h1, h2, h3, h4, h5, h6 { color: #FFF; margin-top: 20px; }")
        styleSheet.addRule("h1 { font-size: 1.6em; }")
        styleSheet.addRule("h2 { font-size: 1.4em; }")
        styleSheet.addRule("h3 { font-size: 1.2em; }")
        styleSheet.addRule("li { margin: 5px 0; }")

        val htmlEditorKit = HTMLEditorKit()
        htmlEditorKit.styleSheet = styleSheet
        markdownDisplay.editorKit = htmlEditorKit

        markdownDisplay.text = htmlContent
        markdownDisplay.isEditable = false
        val scrollPane =
          JScrollPane(markdownDisplay).apply {
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
          }
        add(scrollPane, BorderLayout.CENTER)

        val hideToolWindowButton = JButton("Hide")
        hideToolWindowButton.addActionListener { toolWindow.hide(null) }
        add(hideToolWindowButton, BorderLayout.PAGE_END)
        //        val markdownDisplay = JEditorPane("text/html", htmlContent).apply { isEditable =
        // false }
        //        val scrollPane = JScrollPane(markdownDisplay)
        //        add(scrollPane, BorderLayout.CENTER)
        //
        //        val hideToolWindowButton = JButton("Hide")
        //        hideToolWindowButton.addActionListener { toolWindow.hide(null) }
        //        add(hideToolWindowButton, BorderLayout.PAGE_END)
      }
    }
  }
}
