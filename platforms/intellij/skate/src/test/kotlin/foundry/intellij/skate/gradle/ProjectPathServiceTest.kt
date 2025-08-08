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

class ProjectPathServiceTest {

  @Test
  fun `parseProjectPaths filters comments and blank lines`() {
    val content =
      """
      # This is a comment
      :platforms:gradle:foundry-gradle-plugin
      :platforms:intellij:skate

      # Another comment
      :tools:cli
      :tools:foundry-common
    """
        .trimIndent()

    @Suppress("UNCHECKED_CAST") val result = parseProjectPaths(content)

    assertThat(result)
      .containsExactly(
        ":platforms:gradle:foundry-gradle-plugin",
        ":platforms:intellij:skate",
        ":tools:cli",
        ":tools:foundry-common",
      )
  }

  @Test
  fun `getMatchingProjectPaths returns filtered results`() {
    val testPaths =
      setOf(
        ":platforms:gradle:foundry-gradle-plugin",
        ":platforms:intellij:skate",
        ":platforms:intellij:compose",
        ":tools:cli",
        ":tools:foundry-common",
      )

    val filtered = testPaths.filter { it.startsWith(":platforms:") }.sorted()

    assertThat(filtered).hasSize(3)
    assertThat(filtered).contains(":platforms:gradle:foundry-gradle-plugin")
    assertThat(filtered).contains(":platforms:intellij:compose")
    assertThat(filtered).contains(":platforms:intellij:skate")
  }
}
