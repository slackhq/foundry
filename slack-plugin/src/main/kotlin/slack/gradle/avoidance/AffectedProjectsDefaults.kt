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
package slack.gradle.avoidance

import java.util.Collections.unmodifiableSet

public object AffectedProjectsDefaults {
  public val DEFAULT_INCLUDE_PATTERNS: Set<String> =
    unmodifiableSet(
      setOf(
        "**/*.kt",
        "*.gradle",
        "**/*.gradle",
        "*.gradle.kts",
        "**/*.gradle.kts",
        "**/*.java",
        "**/AndroidManifest.xml",
        "**/res/**",
        "**/src/*/resources/**",
        "gradle.properties",
        "**/gradle.properties",
      )
    )

  public val DEFAULT_NEVER_SKIP_PATTERNS: Set<String> =
    unmodifiableSet(
      setOf(
        // root build.gradle.kts and settings.gradle.kts files
        "*.gradle.kts",
        "*.gradle",
        // root gradle.properties file
        "gradle.properties",
        // Version catalogs
        "**/*.versions.toml",
        // Gradle wrapper files
        "**/gradle/wrapper/**",
        "gradle/wrapper/**",
        "gradlew",
        "gradlew.bat",
        "**/gradlew",
        "**/gradlew.bat",
        // buildSrc
        "buildSrc/**",
        // CI
        ".github/workflows/**",
      )
    )
}
