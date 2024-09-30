/*
 * Copyright (C) 2022 Slack Technologies, LLC
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
package foundry.cli

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import slack.cli.Toml

class TomlTest {
  @Test
  fun simple() {
    // language=toml
    val toml =
      """
      [versions]
      kotlin = "1.6.10" # Trailing comment is skipped
      # Skipped
      jvmTarget = "1.8"
    """
        .trimIndent()
    val versions = Toml.parseVersion(toml.lineSequence())
    assertThat(versions).containsExactly("kotlin", "1.6.10", "jvmTarget", "1.8")
  }

  @Test
  fun everythingAfterPluginsIsSkipped() {
    // language=toml
    val toml =
      """
      [versions]
      kotlin = "1.6.10"
      jvmTarget = "1.8"

      [libraries]
      # Skipped
      jvmTarget = "17"
    """
        .trimIndent()
    val versions = Toml.parseVersion(toml.lineSequence())
    assertThat(versions).containsExactly("kotlin", "1.6.10", "jvmTarget", "1.8")
  }
}
