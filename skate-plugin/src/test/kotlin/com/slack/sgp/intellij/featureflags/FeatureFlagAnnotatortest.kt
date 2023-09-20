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

class FeatureFlagAnnotatortest : LightPlatformCodeInsightFixture4TestCase() {

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
    val content =
      """
            @FeatureFlags(ForComplianceFeature::class)
            enum class ComplianceFeature(override val dependencies: Set<FeatureFlagEnum> = emptySet()) :
              FeatureFlagEnum {
              /** Enables validation of app-scoped environment variant */
              @FeatureFlag(defaultValue = false, minimization = UNAUTHENTICATED) ANDROID_ENV_VARIANT_VALIDATION,
              @FeatureFlag(defaultValue = false, minimization = UNAUTHENTICATED)
              ANDROID_GOV_SLACK_CUSTOM_AWARENESS,
              @FeatureFlag(defaultValue = false, minimization = AUTHENTICATED)
              ANDROID_GOV_SLACK_CUSTOM_AWARENESS_TEAM_SWITCH;
              override val key: String by computeKey()
            }
        """

    // Create a KtFile from the content
    val psiFile = createKotlinFile("ComplianceFeature.kt", content)
    val featureFlags = featureFlagExtractor.extractFeatureFlags(psiFile)
    // Assertions
    assertTrue(featureFlags.contains("ANDROID_ENV_VARIANT_VALIDATION"))
    assertTrue(featureFlags.contains("ANDROID_GOV_SLACK_CUSTOM_AWARENESS"))
    assertTrue(featureFlags.contains("ANDROID_GOV_SLACK_CUSTOM_AWARENESS_TEAM_SWITCH"))
  }

  private fun runAnnotator(enabled: Boolean): List<FeatureFlagSymbol> {
    project.service<SkatePluginSettings>().isLinkifiedFeatureFlagsEnabled = enabled
    val content =
      """
            @FeatureFlags(ForComplianceFeature::class)
            enum class ComplianceFeature(override val dependencies: Set<FeatureFlagEnum> = emptySet()) :
              FeatureFlagEnum {
              /** Enables validation of app-scoped environment variant */
              @FeatureFlag(defaultValue = false, minimization = UNAUTHENTICATED) ANDROID_ENV_VARIANT_VALIDATION,
              @FeatureFlag(defaultValue = false, minimization = UNAUTHENTICATED)
              ANDROID_GOV_SLACK_CUSTOM_AWARENESS,
              @FeatureFlag(defaultValue = false, minimization = AUTHENTICATED)
              ANDROID_GOV_SLACK_CUSTOM_AWARENESS_TEAM_SWITCH;
              override val key: String by computeKey()
            }
        """
    val file = createKotlinFile("ComplianceFeature.kt", content)
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
