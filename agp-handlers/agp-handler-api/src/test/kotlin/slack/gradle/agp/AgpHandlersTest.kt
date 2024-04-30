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
package slack.gradle.agp

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AgpHandlersTest {
  @Test
  fun parseTest() {
    assertVersion("8.0.0-alpha01")
    assertVersion("8.0.0-beta01")
    assertVersion("8.0.0-beta11")
    assertVersion("8.0.0-rc11")
    assertVersion("8.0.0-dev")
  }

  private fun assertVersion(version: String) {
    assertThat(computeAndroidPluginVersion(version).toString())
      .isEqualTo("Android Gradle Plugin version $version")
  }
}
