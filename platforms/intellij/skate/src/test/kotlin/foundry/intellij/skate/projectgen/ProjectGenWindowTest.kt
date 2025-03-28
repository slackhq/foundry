package foundry.intellij.skate.projectgen

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.runInEdtAndWait
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.intellij.openapi.actionSystem.Presentation

class ProjectGenWindowTest : LightPlatformTestCase() {
    fun testDialogTriggersOkCallbackWithoutDeadlock() {
        val latch = CountDownLatch(1)

        runInEdtAndWait {
            val event = AnActionEvent.createFromDataContext(
                "ProjectGenWindowTest",
                Presentation(),
                DataContext { key -> if (key == "project") project else null }
            )

            val dialog = ProjectGenWindow(project, event).apply {
                onOk = {
                    latch.countDown()
                }
            }

            dialog.doOKAction()
        }

        val completed = latch.await(5, TimeUnit.SECONDS)
        assertTrue("Callback didn't complete in time", completed)
    }
}
