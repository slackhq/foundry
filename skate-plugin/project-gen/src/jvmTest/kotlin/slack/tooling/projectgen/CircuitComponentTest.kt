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
package slack.tooling.projectgen

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import slack.tooling.projectgen.circuitgen.FakeCircuitComponent

class CircuitComponentTest {
  @Test
  fun testGenerateCircuitComponentWithNullPackage() {
    val component = FakeCircuitComponent(false)
    val result = component.generate(null, "Foo")
    val expectedContent = "\nclass FooFake"
    assertThat(result).isEqualTo(expectedContent)
  }

  @Test
  fun testGenerateCircuitComponentWithPackage() {
    val component = FakeCircuitComponent(false)
    val result = component.generate("com.example.feature", "Foo")
    val expectedContent =
      """
      package com.example.feature

      class FooFake
      """
        .trimIndent()
    assertThat(result).isEqualTo(expectedContent)
  }
}
