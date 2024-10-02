/*
 * Copyright (C) 2024 Slack Technologies, LLC
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
package foundry.intellij.skate.aibot

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.ContentFactory
import foundry.intellij.compose.aibot.ChatPanel
import foundry.intellij.skate.SkatePluginSettings
import java.util.function.Supplier

class ChatBotListener(private val project: Project) : ToolWindowManagerListener {
  val settings = project.service<SkatePluginSettings>()
  private val CHAT_BOT_ID = "DevXPAI"

  init {
    if (settings.isAIBotEnabled) {
      registerToolWindow()
    }
  }

  private fun registerToolWindow() {
    val toolWindowManager = ToolWindowManager.getInstance(project)
    toolWindowManager.invokeLater {
      if (toolWindowManager.getToolWindow(CHAT_BOT_ID) == null) {
        val toolWindow =
          toolWindowManager.registerToolWindow(CHAT_BOT_ID) {
            stripeTitle = Supplier { "DevXP AI" }
            anchor = ToolWindowAnchor.RIGHT
          }

        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(ChatPanel.createPanel(), "", false)
        toolWindow.contentManager.addContent(content)

        toolWindow.show()
      }
    }
  }
}
