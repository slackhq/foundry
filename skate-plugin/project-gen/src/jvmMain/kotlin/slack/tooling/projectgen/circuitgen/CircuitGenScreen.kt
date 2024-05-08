package slack.tooling.projectgen.circuitgen

import androidx.compose.runtime.snapshots.SnapshotStateList
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import slack.tooling.projectgen.UiElement

internal object CircuitGenScreen : Screen {
  data class State(
    val uiElements: SnapshotStateList<UiElement>,
    val eventSink: (Event) -> Unit,
  ) : CircuitUiState

  sealed interface Event : CircuitUiEvent {
    object Generate : Event
  }
}