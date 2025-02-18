/*
 * Copyright (C) 2025 Slack Technologies, LLC
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
package foundry.skippy

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SkippyConfigTest {

  @Test
  fun `overlayWith combines include patterns when overlayGlobalIncludes is true`() {
    val baseConfig =
      SkippyConfig(
        tool = "local",
        _includePatterns = setOf("baseInclude"),
        overlayGlobalIncludes = true,
      )
    val otherConfig = SkippyConfig(tool = "local", _includePatterns = setOf("otherInclude"))

    val result = baseConfig.overlayWith(otherConfig)

    assertThat(result.includePatterns).containsExactly("baseInclude", "otherInclude")
  }

  @Test
  fun `overlayWith keeps original include patterns when overlayGlobalIncludes is false`() {
    val baseConfig =
      SkippyConfig(
        tool = "local",
        _includePatterns = setOf("baseInclude"),
        overlayGlobalIncludes = false,
      )
    val otherConfig =
      SkippyConfig(tool = SkippyConfig.GLOBAL_TOOL, _includePatterns = setOf("globalInclude"))

    val result = baseConfig.overlayWith(otherConfig)

    assertThat(result.includePatterns).containsExactly("baseInclude")
  }

  @Test
  fun `overlayWith combines exclude patterns when overlayGlobalExcludes is true`() {
    val baseConfig =
      SkippyConfig(
        tool = "local",
        _excludePatterns = setOf("baseExclude"),
        overlayGlobalExcludes = true,
      )
    val otherConfig = SkippyConfig(tool = "local", _excludePatterns = setOf("otherExclude"))

    val result = baseConfig.overlayWith(otherConfig)

    assertThat(result.excludePatterns).containsExactly("baseExclude", "otherExclude")
  }

  @Test
  fun `overlayWith keeps original exclude patterns when overlayGlobalExcludes is false`() {
    val baseConfig =
      SkippyConfig(
        tool = "local",
        _excludePatterns = setOf("baseExclude"),
        overlayGlobalExcludes = false,
      )
    val otherConfig =
      SkippyConfig(tool = SkippyConfig.GLOBAL_TOOL, _excludePatterns = setOf("globalExclude"))

    val result = baseConfig.overlayWith(otherConfig)

    assertThat(result.excludePatterns).containsExactly("baseExclude")
  }

  @Test
  fun `overlayWith combines neverSkip patterns when overlayGlobalSkips is true`() {
    val baseConfig =
      SkippyConfig(
        tool = "local",
        _neverSkipPatterns = setOf("baseNeverSkip"),
        overlayGlobalSkips = true,
      )
    val otherConfig = SkippyConfig(tool = "local", _neverSkipPatterns = setOf("otherNeverSkip"))

    val result = baseConfig.overlayWith(otherConfig)

    assertThat(result.neverSkipPatterns).containsExactly("baseNeverSkip", "otherNeverSkip")
  }

  @Test
  fun `overlayWith keeps original neverSkip patterns when overlayGlobalSkips is false`() {
    val baseConfig =
      SkippyConfig(
        tool = "local",
        _neverSkipPatterns = setOf("baseNeverSkip"),
        overlayGlobalSkips = false,
      )
    val otherConfig =
      SkippyConfig(tool = SkippyConfig.GLOBAL_TOOL, _neverSkipPatterns = setOf("globalNeverSkip"))

    val result = baseConfig.overlayWith(otherConfig)

    assertThat(result.neverSkipPatterns).containsExactly("baseNeverSkip")
  }

  @Test
  fun `overlayWith combines all patterns when other config is not global`() {
    val baseConfig =
      SkippyConfig(
        tool = SkippyConfig.GLOBAL_TOOL,
        _includePatterns = setOf("baseInclude"),
        _excludePatterns = setOf("baseExclude"),
        _neverSkipPatterns = setOf("baseNeverSkip"),
      )
    val otherConfig =
      SkippyConfig(
        tool = "local",
        _includePatterns = setOf("otherInclude"),
        _excludePatterns = setOf("otherExclude"),
        _neverSkipPatterns = setOf("otherNeverSkip"),
      )

    val result = baseConfig.overlayWith(otherConfig)

    assertThat(result.includePatterns).containsExactly("baseInclude", "otherInclude")
    assertThat(result.excludePatterns).containsExactly("baseExclude", "otherExclude")
    assertThat(result.neverSkipPatterns).containsExactly("baseNeverSkip", "otherNeverSkip")
  }
}
