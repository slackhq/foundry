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

class ChatBotListener(private val project: Project): ToolWindowManagerListener {
    val settings = project.service<SkatePluginSettings>()
    private val CHAT_BOT_ID = "DevXPAI"

    init {
        if(settings.isAIBotEnabled){
            registerToolWindow()
        }
    }

    private fun registerToolWindow(){
        val toolWindowManager = ToolWindowManager.getInstance(project)
        toolWindowManager.invokeLater{
            if(toolWindowManager.getToolWindow(CHAT_BOT_ID) == null){
                val toolWindow = toolWindowManager.registerToolWindow(CHAT_BOT_ID){
                    stripeTitle = Supplier {"DevXP AI"}
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