package com.slack.sgp.intellij

import com.automation.remarks.junit5.Video
import com.google.common.truth.Truth.assertThat
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitForIgnoringError
import com.slack.sgp.intellij.pages.ToolWindowFixture
import com.slack.sgp.intellij.pages.idea
import com.slack.sgp.intellij.utils.RemoteRobotExtension
import com.slack.sgp.intellij.utils.StepsLogger
import java.awt.event.KeyEvent.VK_A
import java.awt.event.KeyEvent.VK_META
import java.awt.event.KeyEvent.VK_SHIFT
import java.time.Duration
import java.time.Duration.ofMinutes
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

// TODO:
//  Writes a sample changelog file to the project dir (before opening the project)
//  Writes the setting to the skate config
//  Opens the project
//  Asserts that the panel opened and showed the changelog

@ExtendWith(RemoteRobotExtension::class)
class SkatePluginTest {

  init {
    StepsLogger.init()
  }

  @BeforeEach
  fun waitForIde(remoteRobot: RemoteRobot) {
    waitForIgnoringError(ofMinutes(3)) { remoteRobot.callJs("true") }
  }

  @AfterEach
  fun closeProject(remoteRobot: RemoteRobot) =
    with(remoteRobot) {
      idea {
        if (remoteRobot.isMac()) {
          keyboard {
            hotKey(VK_SHIFT, VK_META, VK_A)
            enterText("Close Project")
            enter()
          }
        } else {
          menuBar.select("File", "Close Project")
        }
      }
    }

  @Test
  @Disabled
  @Video
  fun checkToolWindow(remoteRobot: RemoteRobot) =
    with(remoteRobot) {
      val toolWindow = find(ToolWindowFixture::class.java, timeout = Duration.ofSeconds(10))
      assertThat(toolWindow.window.isShowing).isTrue()
    }

  //  @Test
  //  fun testToolWindowExists() {
  //    val robot = RemoteRobot("http://127.0.0.1:8082")
  //    checkToolWindow(robot)
  //  }
  //  @Test
  //  fun checkToolWindow() {
  //    // Create a RemoteRobot instance with the default URL (localhost:8082)
  //    val robot = RemoteRobot("http://127.0.0.1:8082")
  //
  //    // See if Tool Window has the same name it's supposed to
  //    val toolWindow =
  //      robot.find(
  //        ComponentFixture::class.java,
  //        byXpath("//div[@accessibilityName=\"What's New in Slack!\"]")
  //      )
  //
  //    // Check if the Tool Window is showing
  //    assertThat(toolWindow.isShowing).isTrue()
  //  }
}
