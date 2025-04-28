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
package foundry.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GradlePathUtilsTest {
  @Test
  fun `calling calling convertProjectPathToAccessor with an empty path after root`() {
    val path = ":"
    val result = convertProjectPathToAccessor(path)
    assertThat(result).isEmpty()
  }

  @Test
  fun `calling convertProjectPathToAccessor is camelCased from hyphenated path`() {
    val testPath = ":libraries:activity-feed"
    val result = "projects." + convertProjectPathToAccessor(testPath)
    assertThat(result).isEqualTo("projects.libraries.activityFeed")
  }

  @Test
  fun `calling convertProjectPathToAccessor with a single segment and no hyphen`() {
    val path = ":root"
    val result = "projects." + convertProjectPathToAccessor(path)
    assertThat(result).isEqualTo("projects.root")
  }

  @Test
  fun `calling convertProjectPathToAccessor with multiple nested segments with hyphens`() {
    val path = ":libs:core-utils:net"
    val result = "projects." + convertProjectPathToAccessor(path)
    assertThat(result).isEqualTo("projects.libs.coreUtils.net")
  }

  @Test
  fun `calling calling convertProjectPathToAccessor with a multi hyphen set of words`() {
    val path = ":libs:more-complex-module-name"
    val result = "projects." + convertProjectPathToAccessor(path)
    assertThat(result).isEqualTo("projects.libs.moreComplexModuleName")
  }
}
