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
package foundry.intellij.skate.ideinstall

import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Paths
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class InstallationLocationServiceTest : BasePlatformTestCase() {
  private lateinit var service: InstallationLocationService

  override fun setUp() {
    super.setUp()
    service = InstallationLocationService(project)
  }

  @Test
  fun `path and pattern should match exactly`() {
    val path = Paths.get("/sample/test/path")
    val pattern = "/sample/test/path"

    val result = service.matchesPattern(path, pattern)
    assertThat(result).isTrue()
  }

  @Test
  fun `empty pattern should match`() {
    val path = Paths.get("")
    val pattern = ""
    val result = service.matchesPattern(path, pattern)
    assertThat(result).isTrue()
  }

  @Test
  fun `user home expands properly`() {
    val userHome = System.getProperty("user.home")
    val path = Paths.get("$userHome/sample")
    val pattern = "~/sample"
    val result = service.matchesPattern(path, pattern)
    assertTrue(result)
  }
}
