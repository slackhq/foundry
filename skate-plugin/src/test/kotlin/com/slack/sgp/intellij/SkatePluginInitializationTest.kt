package com.slack.sgp.intellij

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SkatePluginInitializationTest : BasePlatformTestCase() {

  fun `test Skate Plugin Service Initialization to ensure SkateProjectService is properly registered & initialized`() {
    val skateService = project.service<SkateProjectService>()

    // Service should be an instance of SkateProjectServiceImpl
    assertThat(skateService).isInstanceOf(SkateProjectServiceImpl::class.java)
  }

  fun `test Skate Plugin Settings Initialization`() {
    val settings = project.service<SkatePluginSettings>()

    // Assert that settings is not null
    assertThat(settings).isNotNull()

    // Check the default values
    assertThat(settings.whatsNewFilePath).isEqualTo("CHANGELOG.md")
    assertThat(settings.isWhatsNewEnabled).isTrue()
  }
}
