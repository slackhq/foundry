/*
 * Copyright (C) 2026 Slack Technologies, LLC
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
package foundry.gradle.android

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class AndroidCompileSdkTest {

  @Test
  fun majorOnly() {
    assertThat(AndroidCompileSdk.parse("36")).isEqualTo(AndroidCompileSdk(36, null))
  }

  @Test
  fun majorAndMinor() {
    assertThat(AndroidCompileSdk.parse("37.2")).isEqualTo(AndroidCompileSdk(37, 2))
  }

  @Test
  fun legacyPrefix() {
    assertThat(AndroidCompileSdk.parse("android-37.2")).isEqualTo(AndroidCompileSdk(37, 2))
  }

  @Test
  fun invalidMinor() {
    assertThrows(IllegalStateException::class.java) { AndroidCompileSdk.parse("37.x") }
  }
}
