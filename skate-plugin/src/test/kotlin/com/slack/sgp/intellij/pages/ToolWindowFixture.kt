package com.slack.sgp.intellij.pages

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.DefaultXpath
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.search.locators.byXpath
import java.time.Duration

fun RemoteRobot.toolWindow(function: ToolWindowFixture.() -> Unit) {
  find(ToolWindowFixture::class.java, Duration.ofSeconds(10)).apply(function)
}

@FixtureName("Tool Window")
@DefaultXpath("type", "//div[@accessibilityName=\"What's New in Slack!\"]")
class ToolWindowFixture(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) :
  CommonContainerFixture(remoteRobot, remoteComponent) {
  val window
    get() =
      find(
        ComponentFixture::class.java,
        byXpath("//div[@accessibilityName=\"What's New in Slack!\"]")
      )
}
