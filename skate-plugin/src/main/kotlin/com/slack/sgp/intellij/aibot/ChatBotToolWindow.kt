package com.slack.sgp.intellij.aibot

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class ChatBotToolWindow : ToolWindowFactory {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val contentFactory = ContentFactory.getInstance()
    val chatBotActionService = ChatBotActionService()
    val contentPanel = ChatWindow(chatBotActionService)
    val createContent = contentFactory?.createContent(contentPanel, "DevXP AI", false)
    toolWindow.contentManager.addContent(createContent!!)
  }
}