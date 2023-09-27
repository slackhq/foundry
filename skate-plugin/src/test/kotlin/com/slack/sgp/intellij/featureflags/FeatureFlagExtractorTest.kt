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
import com.slack.sgp.intellij.featureflags.FeatureFlagExtractor.ANNOTATION_EMPTY_ERROR
import com.slack.sgp.intellij.featureflags.FeatureFlagExtractor.BASE_URL_EMPTY_ERROR
import com.slack.sgp.intellij.featureflags.FeatureFlagExtractor.BASE_URL_QUERY_PARAM_ERROR
import org.junit.Test

class FeatureFlagExtractorTest : BaseFeatureFlagTest() {

  @Test
  fun `test throws error when featureFlagBaseUrl is empty`() {
    project.service<SkatePluginSettings>().featureFlagBaseUrl = ""
    val file = createKotlinFile("TestFeature.kt", fileContent)
    try {
      FeatureFlagAnnotator().collectInformation(file)
      fail("Expected an IllegalArgumentException to be thrown, but it wasn't.")
    } catch (e: IllegalArgumentException) {
      assertThat(e).hasMessageThat().contains(BASE_URL_EMPTY_ERROR)
    }
  }

  @Test
  fun `test throws error when featureFlagBaseUrl doesn't end with query param`() {
    project.service<SkatePluginSettings>().featureFlagBaseUrl = "https://example.com/"
    val file = createKotlinFile("TestFeature.kt", fileContent)
    try {
      FeatureFlagAnnotator().collectInformation(file)
      fail("Expected an IllegalArgumentException to be thrown, but it wasn't.")
    } catch (e: IllegalArgumentException) {
      assertThat(e).hasMessageThat().contains(BASE_URL_QUERY_PARAM_ERROR)
    }
  }

  @Test
  fun `test throws error when featureFlagAnnotation is empty`() {
    project.service<SkatePluginSettings>().featureFlagBaseUrl = "test.com?q="
    project.service<SkatePluginSettings>().featureFlagAnnotation = ""
    val file = createKotlinFile("TestFeature.kt", fileContent)
    try {
      FeatureFlagAnnotator().collectInformation(file)
      fail("Expected an IllegalArgumentException to be thrown, but it wasn't.")
    } catch (e: IllegalArgumentException) {
      assertThat(e).hasMessageThat().contains(ANNOTATION_EMPTY_ERROR)
    }
  }

  @Test
  fun `test extraction of feature flags from provided content`() {
    project.service<SkatePluginSettings>().featureFlagBaseUrl = "test.com?q="
    project.service<SkatePluginSettings>().featureFlagAnnotation = "slack.featureflag.FeatureFlag"
    val psiFile = createKotlinFile("TestFeature.kt", fileContent)
    val featureFlags = FeatureFlagExtractor.extractFeatureFlags(psiFile)
    val flagUrls = featureFlags.map { it.url }
    assertThat(flagUrls)
      .containsExactly(
        "test.com?q=test_flag_one",
        "test.com?q=flag_two",
        "test.com?q=flag_three",
      )
  }
}
