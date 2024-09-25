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
package slack.gradle

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import slack.gradle.agp.VersionNumber

class SlackGradleUtilTest {

  @Test
  fun standardGitParse() {
    val gitVersionOutput = "git version 2.24.0"
    val parsed = parseGitVersion(gitVersionOutput)
    assertThat(parsed.toString()).isEqualTo("2.24.0")
  }

  @Test
  fun shortGitParse() {
    val gitVersionOutput = "git version 2.24"
    val parsed = parseGitVersion(gitVersionOutput)
    assertThat(parsed.toString()).isEqualTo("2.24.0")
  }

  @Test
  fun hubParse() {
    // GitHub's "hub" tool changes the output of 'git --version'
    val gitVersionOutput =
      """
      git version 2.24.0
      hub version 2.13.0
      """
        .trimIndent()
    val parsed = parseGitVersion(gitVersionOutput)
    assertThat(parsed.toString()).isEqualTo("2.24.0")
  }

  @Test
  fun appleGit() {
    // Apple appends some stuff on the end
    val gitVersionOutput =
      """
      git version 2.37.1 (Apple Git-137.1)
      """
        .trimIndent()
    val parsed = parseGitVersion(gitVersionOutput)
    assertThat(parsed.toString()).isEqualTo("2.37.1")
  }

  @Test
  fun unrecognizedFallsBackToUnknown() {
    val gitVersionOutput =
      """
      garbage
      """
        .trimIndent()
    val parsed = parseGitVersion(gitVersionOutput)
    assertThat(parsed).isEqualTo(VersionNumber.UNKNOWN)
  }

  @Test
  fun nullFallsBackToUnknown() {
    val parsed = parseGitVersion(null)
    assertThat(parsed).isEqualTo(VersionNumber.UNKNOWN)
  }
}
