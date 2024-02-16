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
