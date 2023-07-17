package com.slack.sgp.intellij

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SkatePluginInitializationTest : BasePlatformTestCase() {

  fun `test Skate Plugin Service Initialization to ensure SkateProjectService is properly registered & initialized`() {
    val skateService = project.service<SkateProjectService>()
    assertTrue(
      "Service should be instance of SkateProjectServiceImpl",
      skateService is SkateProjectServiceImpl
    )
  }
}
