// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package com.slack.sgp.intellij.sidepanel

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import org.jetbrains.annotations.NotNull

class WhatsNewPanel : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(@NotNull project: Project, @NotNull toolWindow: ToolWindow) {
    val toolWindowContent = CalendarToolWindowContent(toolWindow)
    val content =
      ContentFactory.getInstance().createContent(toolWindowContent.contentPanel, "", false)
    toolWindow.contentManager.addContent(content)
  }

  private class CalendarToolWindowContent(toolWindow: ToolWindow) {

    val contentPanel: JPanel =
      JPanel().apply {
        layout = BorderLayout(0, 20)
        border = BorderFactory.createEmptyBorder(40, 0, 0, 0)
        add(createControlsPanel(toolWindow), BorderLayout.CENTER)
      }

    @NotNull
    private fun createControlsPanel(toolWindow: ToolWindow): JPanel {

      return JPanel().apply {
        val helloWorldLabel = JLabel("Hello World!")
        add(helloWorldLabel)

        val hideToolWindowButton = JButton("Hide")
        hideToolWindowButton.addActionListener { toolWindow.hide(null) }
        add(hideToolWindowButton)
      }
    }
  }
}
