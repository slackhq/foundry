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
package foundry.skippy

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Represents a Skippy configuration for a specific [tool].
 *
 * @property buildUponDefaults Whether to build upon the default Skippy configuration.
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
 * @property overlayGlobalIncludes Whether to include global default include patterns when
 *   overlaying configurations.
 * @property overlayGlobalExcludes Whether to include global default exclude patterns when
 *   overlaying configurations.
 * @property overlayGlobalSkips Whether to include global default never-skip patterns when
 *   overlaying configurations.
 */
@JsonClass(generateAdapter = true)
public data class SkippyConfig(
  public val tool: String,
  public val buildUponDefaults: Boolean = false,
  @Json(name = "includePatterns") internal val _includePatterns: Set<String> = emptySet(),
  @Json(name = "excludePatterns") internal val _excludePatterns: Set<String> = emptySet(),
  @Json(name = "neverSkipPatterns") internal val _neverSkipPatterns: Set<String> = emptySet(),
  internal val overlayGlobalIncludes: Boolean = true,
  internal val overlayGlobalExcludes: Boolean = true,
  internal val overlayGlobalSkips: Boolean = true,
) {

  public val isGlobal: Boolean
    get() = tool == GLOBAL_TOOL

  public val includePatterns: Set<String> = buildSet {
    addAll(_includePatterns)
    if (buildUponDefaults) {
      addAll(AffectedProjectsDefaults.DEFAULT_INCLUDE_PATTERNS)
    }
  }
  public val excludePatterns: Set<String> = _excludePatterns
  public val neverSkipPatterns: Set<String> = buildSet {
    addAll(_neverSkipPatterns)
    if (buildUponDefaults) {
      addAll(AffectedProjectsDefaults.DEFAULT_NEVER_SKIP_PATTERNS)
    }
  }

  public fun overlayWith(other: SkippyConfig): SkippyConfig {
    return copy(
      _includePatterns =
        if (!other.isGlobal || overlayGlobalIncludes) {
          includePatterns + other.includePatterns
        } else {
          includePatterns
        },
      _excludePatterns =
        if (!other.isGlobal || overlayGlobalExcludes) {
          excludePatterns + other.excludePatterns
        } else {
          excludePatterns
        },
      _neverSkipPatterns =
        if (!other.isGlobal || overlayGlobalSkips) {
          neverSkipPatterns + other.neverSkipPatterns
        } else {
          neverSkipPatterns
        },
    )
  }

  public companion object {
    public const val GLOBAL_TOOL: String = "global"
  }
}
