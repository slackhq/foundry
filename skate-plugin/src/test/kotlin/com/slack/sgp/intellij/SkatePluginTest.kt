package com.slack.sgp.intellij

import com.google.common.truth.Truth.assertThat
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import org.junit.Test

class SkatePluginTest {

  @Test
  fun checkToolWindow() {
    // Create a RemoteRobot instance with the default URL (localhost:8082)
    val robot = RemoteRobot("http://127.0.0.1:8082")

    // See if Tool Window has the same name it's supposed to
    val toolWindow =
      robot.find(
        ComponentFixture::class.java,
        byXpath("//div[@accessibilityName=\"What's New in Slack!\"]")
      )

    // Check if the Tool Window is showing
    assertThat(toolWindow.isShowing).isTrue()
  }
}
