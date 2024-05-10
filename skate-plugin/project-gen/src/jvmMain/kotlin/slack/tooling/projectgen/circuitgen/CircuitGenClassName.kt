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
package slack.tooling.projectgen.circuitgen

import com.squareup.kotlinpoet.ClassName

object CircuitGenClassNames {
  val APP_SCOPE = ClassName("slack.di", "AppScope")
  val CIRCUIT_INJECT = ClassName("com.slack.circuit.codegen.annotations", "CircuitInject")
  val CIRCUIT_UI_EVENT = ClassName("com.slack.circuit.runtime", "CircuitUiEvent")
  val CIRCUIT_UI_STATE = ClassName("com.slack.circuit.runtime", "CircuitUiState")
  val COMPOSABLE = ClassName("androidx.compose.runtime", "Composable")
  val IMMUTABLE = ClassName("androidx.compose.runtime", "Immutable")
  val JAVA_INJECT = ClassName("javax.inject", "Inject")
  val MODIFIER = ClassName("androidx.compose.ui", "Modifier")
  val NAVIGATOR = ClassName("com.slack.circuit.runtime", "Navigator")
  val PARCELIZE = ClassName("kotlinx.parcelize", "Parcelize")
  val PRESENTER = ClassName("com.slack.circuit.runtime.presenter", "Presenter")
  val SCREEN_INTERFACE = ClassName("com.slack.circuit.runtime.screen", "Screen")
  val USER_SCOPE = ClassName("slack.di", "UserScope")
  val SCOPE_MAP = mapOf(
    "User Scope" to USER_SCOPE,
    "App Scope" to APP_SCOPE
  )
}
