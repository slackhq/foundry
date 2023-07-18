import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import com.slack.sgp.intellij.SkatePluginSettings
import com.slack.sgp.intellij.SkateProjectService
import java.io.File
import java.util.function.Supplier
import org.junit.Before
import org.junit.Test

class SkatePluginTest : BasePlatformTestCase() {

  @Before
  fun setUpToolWindow() {
    val toolWindowManager = ToolWindowManager.getInstance(project)
    toolWindowManager.registerToolWindow("skate-whats-new") {
      stripeTitle = Supplier { "What's New in Slack!" }
      anchor = ToolWindowAnchor.RIGHT
    }
  }

  @Test
  fun `test opening project shows changelog panel`() {
    val project = myFixture.project

    val changelogFile = createChangelogFile(project)

    val settings = project.service<SkatePluginSettings>()

    // set default skate config settings
    settings.isWhatsNewEnabled = true
    settings.whatsNewFilePath = changelogFile.absolutePath

    val skateService = project.service<SkateProjectService>()
    skateService.showWhatsNewWindow()

    // account for async functions?
    UIUtil.dispatchAllInvocationEvents()

    // test that the panel is opened.
    val toolWindowManager = ToolWindowManager.getInstance(project)
    val toolWindow = toolWindowManager.getToolWindow("skate-whats-new")
    assertThat(toolWindow).isNotNull()
    if (toolWindow != null) {
      assertThat(toolWindow.isVisible).isTrue()
    }

    val contentManager = toolWindow?.contentManager
    val content = contentManager?.getContent(0)
    assertThat(content).isNotNull()
    if (content != null) {
      assertThat(changelogFile.name).isEqualTo(content.displayName)
    }
  }

  private fun createChangelogFile(project: Project): File {
    val changelogFile = File(project.basePath!!, "changelog.md")
    changelogFile.writeText("# Changelog Sample")
    return changelogFile
  }
}
