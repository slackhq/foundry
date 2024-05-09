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
import java.io.StringWriter
import org.junit.Test
import slack.tooling.projectgen.circuitgen.AssistedInjectionConfig
import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.Companion.APP_SCOPE
import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.Companion.USER_SCOPE
import slack.tooling.projectgen.circuitgen.CircuitPresenter
import slack.tooling.projectgen.circuitgen.CircuitScreen
import slack.tooling.projectgen.circuitgen.CircuitTest

class CircuitComponentTest {
  @Test
  fun testGenerateCircuitScreen() {
    val component = CircuitScreen()
    val resultSpec = component.generate("com.example.feature", "FooScreen")
    val stringWriter = StringWriter()
    resultSpec.writeTo(stringWriter)
    val expectedContent =
      """
      package com.example.feature

      import androidx.compose.runtime.Immutable
      import com.slack.circuit.runtime.CircuitUiEvent
      import com.slack.circuit.runtime.CircuitUiState
      import com.slack.circuit.runtime.screen.Screen
      import kotlin.Unit
      import kotlinx.parcelize.Parcelize

      @Parcelize
      public class FooScreen : Screen {
        public data class State(
          public val eventSink: Event.() -> Unit = {},
        ) : CircuitUiState

        @Immutable
        public sealed interface Event : CircuitUiEvent
      }

      """
        .trimIndent()
    assertThat(stringWriter.toString()).isEqualTo(expectedContent)
  }

  @Test
  fun testGenerateCircuitPresenterNoAssistedInjection() {
    val component = CircuitPresenter(AssistedInjectionConfig(), listOf(USER_SCOPE))
    val resultSpec = component.generate("com.example.feature", "FooPresenter")
    val stringWriter = StringWriter()
    resultSpec.writeTo(stringWriter)
    val expectedContent =
      """
      package com.example.feature

      import androidx.compose.runtime.Composable
      import com.slack.circuit.codegen.annotations.CircuitInject
      import com.slack.circuit.runtime.Navigator
      import com.slack.circuit.runtime.presenter.Presenter
      import slack.di.UserScope

      public class FooPresenter(
        private val screen: FooScreen,
        private val navigator: Navigator,
      ) : Presenter<FooScreen.State> {
        @Composable
        override fun present(): FooScreen.State {
          TODO("Implement me!")
        }

        @CircuitInject(
          FooScreen::class,
          UserScope::class,
        )
        public fun interface Factory {
          public fun create(screen: FooScreen, navigator: Navigator): FooPresenter
        }
      }

      """
        .trimIndent()
    assertThat(stringWriter.toString()).isEqualTo(expectedContent)
  }

  @Test
  fun testGenerateCircuitPresenterWithAssistedInjection() {
    val component =
      CircuitPresenter(AssistedInjectionConfig(screen = true, navigator = true), listOf(APP_SCOPE))
    val resultSpec = component.generate("com.example.feature", "FooPresenter")
    val stringWriter = StringWriter()
    resultSpec.writeTo(stringWriter)
    val expectedContent =
      """
      package com.example.feature

      import androidx.compose.runtime.Composable
      import com.slack.circuit.codegen.annotations.CircuitInject
      import com.slack.circuit.runtime.Navigator
      import com.slack.circuit.runtime.presenter.Presenter
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      import slack.di.AppScope

      public class FooPresenter @AssistedInject constructor(
        @Assisted
        private val screen: FooScreen,
        @Assisted
        private val navigator: Navigator,
      ) : Presenter<FooScreen.State> {
        @Composable
        override fun present(): FooScreen.State {
          TODO("Implement me!")
        }

        @AssistedFactory
        @CircuitInject(
          FooScreen::class,
          AppScope::class,
        )
        public fun interface Factory {
          public fun create(screen: FooScreen, navigator: Navigator): FooPresenter
        }
      }

      """
        .trimIndent()
    assertThat(stringWriter.toString()).isEqualTo(expectedContent)
  }

  @Test
  fun testGenerateTestClass() {
    val component =
      CircuitTest(fileSuffix = "UiTest", baseClass = "com.example.test.CustomBaseClass")
    val resultSpec = component.generate("com.example.feature.test", "Foo")
    val stringWriter = StringWriter()
    resultSpec.writeTo(stringWriter)
    val expectedContent =
      """
        package com.example.feature.test

        import com.example.test.CustomBaseClass

        public class FooUiTest : CustomBaseClass()

      """
        .trimIndent()
    assertThat(stringWriter.toString()).isEqualTo(expectedContent)
  }
}
