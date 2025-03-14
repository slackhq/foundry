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
package foundry.gradle.topography

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModuleTopographyTest {

  @Test
  fun `removeEmptyBraces should remove single empty braces block`() {
    val input = "exampleBlock { }"
    val actual = input.removeEmptyBraces()
    assertThat(actual).isEmpty()
  }

  @Test
  fun `removeEmptyBraces should remove nested empty braces block`() {
    val input =
      """
            outer {
                inner { }
            }
        """
        .trimIndent()
    val actual = input.removeEmptyBraces()
    assertThat(actual).isEmpty()
  }

  @Test
  fun `removeEmptyBraces should handle multiple empty braces blocks`() {
    val input =
      """
            block1 { }
            block2 { }
        """
        .trimIndent()
    val actual = input.removeEmptyBraces()
    assertThat(actual).isEmpty()
  }

  @Test
  fun `removeEmptyBraces should not affect blocks with content`() {
    val input =
      """
            block {
                content
            }
        """
        .trimIndent()
    val expected =
      """
            block {
                content
            }
        """
        .trimIndent()
    val actual = input.removeEmptyBraces()
    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `removeEmptyBraces should handle mixed empty and non-empty braces blocks`() {
    val input =
      """
            block1 {
                content
            }
            block2 { }
        """
        .trimIndent()
    val expected =
      """
            block1 {
                content
            }
        """
        .trimIndent()
    val actual = input.removeEmptyBraces()
    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `removeEmptyBraces should handle no braces in input`() {
    val input = "no braces here"
    val expected = "no braces here"
    val actual = input.removeEmptyBraces()
    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `removeEmptyBraces should handle empty input`() {
    val input = ""
    val expected = ""
    val actual = input.removeEmptyBraces()
    assertThat(actual).isEqualTo(expected)
  }
}
