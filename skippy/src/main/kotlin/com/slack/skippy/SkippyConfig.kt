/*
 * Copyright (C) 2024 Slack Technologies, LLC
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
package com.slack.skippy

import com.squareup.moshi.JsonClass

/**
 * Represents a Skippy configuration for a specific [tool].
 *
 * @property includePatterns A set of glob patterns for files to include in computing affected
 *   projects. This should usually be source files, build files, gradle.properties files, and other
 *   projects that affect builds.
 * @property excludePatterns A set of glob patterns for files to exclude from computing affected
 *   projects. This is run _after_ [includePatterns] and can be useful for excluding files that
 *   would otherwise be included by an existing inclusion pattern.
 * @property neverSkipPatterns A set of glob patterns that, if matched with a file, indicate that
 *   nothing should be skipped and [AffectedProjectsComputer.compute] will return null. This is
 *   useful for globally-affecting things like root build files, `libs.versions.toml`, etc.
 *   **NOTE**: This list is always merged with [includePatterns] as these are implicitly relevant
 *   files.
 */
@JsonClass(generateAdapter = true)
public data class SkippyConfig(
  public val tool: String,
  public val includePatterns: Set<String> = AffectedProjectsDefaults.DEFAULT_INCLUDE_PATTERNS,
  public val excludePatterns: Set<String> = emptySet(),
  public val neverSkipPatterns: Set<String> = AffectedProjectsDefaults.DEFAULT_NEVER_SKIP_PATTERNS,
) {
  public companion object {
    public const val GLOBAL_TOOL: String = "global"
  }

  public fun overlayWith(other: SkippyConfig): SkippyConfig {
    return copy(
      includePatterns = includePatterns + other.includePatterns,
      excludePatterns = excludePatterns + other.excludePatterns,
      neverSkipPatterns = neverSkipPatterns + other.neverSkipPatterns,
    )
  }
}
