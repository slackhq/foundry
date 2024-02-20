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
package com.slack.sgp.intellij.projectgen

import androidx.compose.runtime.snapshots.SnapshotStateList
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import slack.tooling.projectgen.UiElement

internal object ProjectGenScreen : Screen {
  data class State(
    val uiElements: SnapshotStateList<UiElement>,
    // TODO make this a "next page" instead?
    val showDoneDialog: Boolean,
    val showErrorDialog: Boolean,
    val canGenerate: Boolean,
    val eventSink: (Event) -> Unit,
  ) : CircuitUiState

  sealed interface Event : CircuitUiEvent {
    object Generate : Event

    object Quit : Event

    object Reset : Event
  }
}
