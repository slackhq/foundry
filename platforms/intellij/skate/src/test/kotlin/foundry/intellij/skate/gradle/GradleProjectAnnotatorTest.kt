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
import org.junit.Test

class GradleProjectAnnotatorTest {

  @Test
  fun `project call pattern matches valid calls`() {
    // Test valid project calls
    val validCalls =
      mapOf(
        """project(":platforms:intellij:skate")""" to ":platforms:intellij:skate",
        """project(':platforms:intellij:skate')""" to ":platforms:intellij:skate",
        """project( ":platforms:intellij:skate" )""" to ":platforms:intellij:skate",
        """project(  ':platforms:intellij:skate'  )""" to ":platforms:intellij:skate",
        """project(":tools:cli")""" to ":tools:cli",
        """project(':tools:foundry-common')""" to ":tools:foundry-common",
      )

    validCalls.forEach { (call, expectedPath) ->
      val matcher = PROJECT_CALL_PATTERN.matcher(call)
      assertThat(matcher.find()).isTrue()
      assertThat(matcher.group(1)).isEqualTo(expectedPath)
    }
  }

  @Test
  fun `project call pattern does not match invalid calls`() {
    val invalidCalls =
      listOf(
        """project()""",
        """project("")""",
        """project(variable)""",
        """projects(":some:path")""",
        """project(":unclosed""",
        """project':unclosed")""",
      )

    invalidCalls.forEach { call ->
      val matcher = PROJECT_CALL_PATTERN.matcher(call)
      assertThat(matcher.find()).isFalse()
    }
  }

  @Test
  fun `project call pattern extracts paths from complex gradle content`() {
    val gradleContent =
      """
      dependencies {
        implementation(project(":platforms:intellij:skate"))
        implementation(project(':tools:cli'))
        testImplementation(project( ":tools:foundry-common" ))
        api project(':platforms:gradle:foundry-gradle-plugin')
      }
    """
        .trimIndent()

    val matcher = PROJECT_CALL_PATTERN.matcher(gradleContent)
    val matches = mutableListOf<String>()

    while (matcher.find()) {
      matches.add(matcher.group(1))
    }

    assertThat(matches)
      .containsExactly(
        ":platforms:intellij:skate",
        ":tools:cli",
        ":tools:foundry-common",
        ":platforms:gradle:foundry-gradle-plugin",
      )
      .inOrder()
  }
}
