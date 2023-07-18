package com.slack.sgp.intellij

import com.intellij.remoterobot.RemoteRobot
import org.junit.Test

class SkatePluginTest {

  @Test
  fun checkToolWindow() {
    // Create a RemoteRobot instance with the default URL (localhost:8082)
    val robot = RemoteRobot("http://127.0.0.1:8082")
  }
}
