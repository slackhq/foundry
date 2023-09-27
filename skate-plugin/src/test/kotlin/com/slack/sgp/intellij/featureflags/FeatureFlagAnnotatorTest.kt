/*
 * Copyright (C) 2023 Slack Technologies, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.slack.sgp.intellij.featureflags

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.components.service
import com.slack.sgp.intellij.SkatePluginSettings
import org.junit.Test

class FeatureFlagAnnotatorTest : BaseFeatureFlagTest() {

  @Test
  fun `test returns empty list when linkify is disabled`() {
    assertThat(runAnnotator(enabled = false)).isEmpty()
  }

  @Test
  fun `test report symbols when linkify is enabled`() {
    assertThat(runAnnotator(enabled = true)).isNotEmpty()
  }

  private fun runAnnotator(enabled: Boolean): List<FeatureFlagSymbol> {
    project.service<SkatePluginSettings>().isLinkifiedFeatureFlagsEnabled = enabled
    project.service<SkatePluginSettings>().featureFlagBaseUrl = "test.com?q="
    project.service<SkatePluginSettings>().featureFlagAnnotation = "slack.featureflag.FeatureFlag"
    val file = createKotlinFile("TestFeature.kt", fileContent)
    val flags = FeatureFlagAnnotator().collectInformation(file)
    return FeatureFlagAnnotator().doAnnotate(flags)
  }
}
