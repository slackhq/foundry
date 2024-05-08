/*
 * Copyright (C) 2024 Slack Technologies, LLC
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
package com.slack.sgp.intellij

import com.google.common.truth.Truth.assertThat
import com.slack.sgp.intellij.util.getJavaPackageName
import java.nio.file.Path
import org.junit.Test

class PathExtensionTest {

  @Test
  fun testAndroidPackageNameFromFilePath() {
    val path =
      Path.of("/users/src/repo/features/message/src/main/kotlin/com/features/messages/Feature.kt")
    val expectedPackage = "com.features.messages"
    val result = path.getJavaPackageName()
    assertThat(result).isEqualTo(expectedPackage)
  }

  @Test
  fun testAndroidPackageNameFromDirectoryPath() {
    val path = Path.of("/users/src/repo/features/message/src/main/kotlin/com/features/messages")
    val expectedPackage = "com.features.messages"
    val result = path.getJavaPackageName()
    assertThat(result).isEqualTo(expectedPackage)
  }

  @Test
  fun testAndroidPackageNameFromTestPath() {
    val path =
      Path.of("/users/src/repo/features/message/src/test/kotlin/com/features/messages/test")
    val expectedPackage = "com.features.messages.test"
    val result = path.getJavaPackageName()
    assertThat(result).isEqualTo(expectedPackage)
  }

  @Test
  fun testAndroidPackageNull() {
    val path = Path.of("/users/src/repo/build/WhatsNew.md")
    val result = path.getJavaPackageName()
    assertThat(result).isNull()
  }
}
