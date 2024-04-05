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
import org.junit.Assert.assertThrows
import org.junit.Test
import slack.tooling.projectgen.circuitgen.CircuitComponentFactory
import slack.tooling.projectgen.circuitgen.FakeCircuitComponent

class CircuitComponentFactoryTest {
  @Test
  fun testGenerateCircuitComponents() {
    val screen = FakeCircuitComponent(false)
    val presenter = FakeCircuitComponent(false)
    val presenterTest = FakeCircuitComponent(true)
    val uiTest = FakeCircuitComponent(true)

    val components = listOf(screen, presenter, presenterTest, uiTest)
    val directory = "users/repo/src/main/kotlin/com/feature/message"
    val packageName = "com.feature.message"
    CircuitComponentFactory().generateCircuitComponents(directory, packageName, "Foo", components)

    assertThat(screen.directoryCreated).isEqualTo(directory)
    assertThat(presenter.directoryCreated).isEqualTo(directory)
    assertThat(presenterTest.directoryCreated).isEqualTo(directory.replace("src/main", "src/test"))
    assertThat(uiTest.directoryCreated).isEqualTo(directory.replace("src/main", "src/test"))
  }

  @Test
  fun testGenerateComponentsThrowExceptionOnEmptyInput() {
    val screen = FakeCircuitComponent(false)
    val presenter = FakeCircuitComponent(false)
    val components = listOf(screen, presenter)
    assertThrows(IllegalArgumentException::class.java) {
      CircuitComponentFactory().generateCircuitComponents("", "", "Foo", components)
    }
  }
}
