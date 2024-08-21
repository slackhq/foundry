package com.slack.sgp.intellij.aibot

import androidx.compose.ui.awt.ComposePanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class ChatBotToolWindowCompose : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(createComposePanel(), "", false)
        toolWindow.contentManager.addContent(content)
    }

    private fun createComposePanel(): ComposePanel {
        return ComposePanel().apply {
            setContent {
                ChatWindowCompose()
            }
        }
    }
}