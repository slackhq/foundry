package com.slack.sgp.intellij

import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase

class WhatsNewToolWindowTest : BasePlatformTestCase() {
  private var toolWindowId: String = "skate-whats-new"

  override fun setUp() {
    super.setUp()
    val service = SkateProjectServiceImpl(project)
    service.showWhatsNewWindow()
  }

  fun `test Tool Window Exists`() {
    val toolWindowManager = ToolWindowManager.getInstance(project)

    val toolWindowRegistered = toolWindowManager.getToolWindow(toolWindowId)

    TestCase.assertNotNull("Tool Window doesn't exist", toolWindowRegistered)
  }

  override fun tearDown() {
    super.tearDown()
  }
}
