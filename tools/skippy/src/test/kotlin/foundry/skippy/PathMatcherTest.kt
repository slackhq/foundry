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
package foundry.skippy

import com.google.common.truth.Truth.assertThat
import okio.Path.Companion.toPath
import org.junit.Test

class PathMatcherTest {

  @Test
  fun basicTests() {
    "**/*.versions.toml" assertMatches "gradle/libs.versions.toml"
    "**/gradle/wrapper/**" assertMatches "nested/gradle/wrapper/gradle-wrapper.properties"
    "**/gradle/wrapper/**" assertDoesNotMatch "gradle/wrapper/gradle-wrapper.properties"
    "gradle/wrapper/**" assertMatches "gradle/wrapper/gradle-wrapper.properties"
    "gradle/wrapper/**" assertDoesNotMatch "nested/gradle/wrapper/gradle-wrapper.properties"
    "src/test*/**" assertMatches "src/test/foo.kt"
    "src/test*/**" assertMatches "src/testImplementation/foo.kt"
  }

  private infix fun String.assertMatches(value: String) {
    assertMatchesAll(value)
  }

  private fun String.assertMatchesAll(vararg values: String) {
    val pathMatcher = toPathMatcher()
    for (value in values) {
      // Test both Path and String matchers
      assertThat(pathMatcher.matches(value.toPath())).isTrue()
      assertThat(pathMatcher.matches(value)).isTrue()
    }
  }

  private infix fun String.assertDoesNotMatch(value: String) {
    assertDoesNotMatchAll(value)
  }

  private fun String.assertDoesNotMatchAll(vararg values: String) {
    val pathMatcher = toPathMatcher()
    for (value in values) {
      // Test both Path and String matchers
      assertThat(pathMatcher.matches(value.toPath())).isFalse()
      assertThat(pathMatcher.matches(value)).isFalse()
    }
  }
}
