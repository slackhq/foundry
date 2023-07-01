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
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

class WhatsNewPanelFactory : DumbAware {
  fun createToolWindowContent(toolWindow: ToolWindow, projectName: String) {
    val toolWindowContent = WhatsNewPanelContent(toolWindow, projectName)
    val content =
      ContentFactory.getInstance().createContent(toolWindowContent.contentPanel, "", false)
    toolWindow.contentManager.addContent(content)
  }

  private class WhatsNewPanelContent(toolWindow: ToolWindow, projectName: String) {

    val contentPanel: JPanel =
      JPanel().apply {
        layout = BorderLayout(0, 20)
        border = BorderFactory.createEmptyBorder(40, 0, 0, 0)
        add(createControlsPanel(toolWindow, projectName), BorderLayout.CENTER)
      }

    private fun createControlsPanel(toolWindow: ToolWindow, projectName: String): JPanel {

      return JPanel().apply {
        val helloWorldLabel = JLabel(projectName)
        add(helloWorldLabel)

        val hideToolWindowButton = JButton("Hide")
        hideToolWindowButton.addActionListener { toolWindow.hide(null) }
        add(hideToolWindowButton)
      }
    }
  }
}
