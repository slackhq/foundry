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
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import com.slack.sgp.intellij.SkatePluginSettings
import org.junit.Test

class FeatureFlagAnnotatorTest : LightPlatformCodeInsightFixture4TestCase() {
  // language=kotlin
  private val fileContent =
    """
        enum class TestFeatures :
          FeatureFlagEnum {
          @FeatureFlag FLAG_ONE,
          @FeatureFlag FLAG_TWO,
          @FeatureFlag FLAG_THREE,
        }
        """

  @Test
  fun `test returns empty list when linkify is disabled`() {
    assertThat(runAnnotator(enabled = false)).isEmpty()
  }

  @Test
  fun `test report symbols when linkify is enabled`() {
    assertThat(runAnnotator(enabled = true)).isNotEmpty()
  }

  @Test
  fun `test extraction of feature flags from provided content`() {
    val featureFlagExtractor = FeatureFlagExtractor()
    val psiFile = createKotlinFile("TestFeature.kt", fileContent)
    val featureFlags = featureFlagExtractor.extractFeatureFlags(psiFile)
    assertTrue(featureFlags.contains("FLAG_ONE"))
    assertTrue(featureFlags.contains("FLAG_TWO"))
    assertTrue(featureFlags.contains("FLAG_THREE"))
  }

  private fun runAnnotator(enabled: Boolean): List<FeatureFlagSymbol> {
    project.service<SkatePluginSettings>().isLinkifiedFeatureFlagsEnabled = enabled
    val file = createKotlinFile("TestFeature.kt", fileContent)
    FeatureFlagAnnotator().collectInformation(file)
    return FeatureFlagAnnotator().doAnnotate(file)
  }

  private fun createKotlinFile(
    name: String,
    text: String,
  ): PsiFile {
    return myFixture.configureByText(name, text)
  }
}
