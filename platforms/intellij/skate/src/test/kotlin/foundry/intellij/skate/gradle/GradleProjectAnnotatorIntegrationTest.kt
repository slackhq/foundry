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
package foundry.intellij.skate.gradle

import com.google.common.truth.Truth.assertThat
import foundry.intellij.skate.gradle.GradleProjectUtils.parseProjectPaths
import org.junit.Test

class GradleProjectAnnotatorIntegrationTest {

  @Test
  fun `PROJECT_CALL_PATTERN correctly extracts project paths`() {
    val gradleContent =
      """
      dependencies {
        implementation(project(":platforms:intellij:skate"))
        testImplementation(project(':tools:cli'))
        api(project( ":tools:foundry-common" ))
        runtimeOnly project(':platforms:gradle:foundry-gradle-plugin')
      }
    """
        .trimIndent()

    val matcher = PROJECT_CALL_PATTERN.matcher(gradleContent)
    val extractedPaths = mutableListOf<String>()

    while (matcher.find()) {
      extractedPaths.add(matcher.group(1))
    }

    assertThat(extractedPaths)
      .containsExactly(
        ":platforms:intellij:skate",
        ":tools:cli",
        ":tools:foundry-common",
        ":platforms:gradle:foundry-gradle-plugin",
      )
      .inOrder()
  }

  @Test
  fun `PROJECT_CALL_PATTERN rejects invalid patterns`() {
    val invalidPatterns =
      listOf(
        // Empty
        "project()",
        // Variable reference
        "project(variable)",
        // Wrong function name
        "projects(\":valid:path\")",
        // Unclosed quote
        "project(\":unclosed",
        // Malformed
        "project\":unclosed\")",
        // Different function
        "someOtherFunction(\":not:a:project\")",
      )

    for (pattern in invalidPatterns) {
      val matcher = PROJECT_CALL_PATTERN.matcher(pattern)
      assertThat(matcher.find()).isFalse()
    }
  }

  @Test
  fun `annotator filtering logic works correctly`() {
    // Should process: contains project() and no children with project()
    val leafElement = "project(\":tools:cli\")"
    assertThat(shouldAnnotatorProcessElement(leafElement, hasProjectChildren = false)).isTrue()

    // Should skip: contains project() but has children with project()
    val parentElement = "implementation(project(\":tools:cli\"))"
    assertThat(shouldAnnotatorProcessElement(parentElement, hasProjectChildren = true)).isFalse()

    // Should skip: doesn't contain project()
    val nonProjectElement = "implementation(libs.someLibrary)"
    assertThat(shouldAnnotatorProcessElement(nonProjectElement, hasProjectChildren = false))
      .isFalse()
  }

  @Test
  fun `ProjectPathService parsing logic works correctly`() {
    // Needs to skip blank lines and comments
    val testContent =
      """
      # This is a comment
      :platforms:intellij:skate

      :tools:cli
      # Another comment
      :tools:foundry-common

    """
        .trimIndent()

    val parsedPaths = parseProjectPaths(testContent)

    assertThat(parsedPaths)
      .containsExactly(":platforms:intellij:skate", ":tools:cli", ":tools:foundry-common")
    assertThat(parsedPaths).doesNotContain("# This is a comment")
    assertThat(parsedPaths).doesNotContain("")
  }
}
