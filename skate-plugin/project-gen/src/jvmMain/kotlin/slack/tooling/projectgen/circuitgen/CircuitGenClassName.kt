package slack.tooling.projectgen.circuitgen

import com.squareup.kotlinpoet.ClassName

class CircuitGenClassNames {
  companion object {
    val APP_SCOPE = ClassName("slack.di", "AppScope")
    val CIRCUIT_INJECT = ClassName("com.slack.circuit.codegen.annotations", "CircuitInject")
    val CIRCUIT_UI_EVENT = ClassName("com.slack.circuit.runtime", "CircuitUiEvent")
    val CIRCUIT_UI_STATE = ClassName("com.slack.circuit.runtime", "CircuitUiState")
    val COMPOSABLE = ClassName("androidx.compose.runtime", "Composable")
    val IMMUTABLE = ClassName("androidx.compose.runtime", "Immutable")
    val JAVA_INJECT = ClassName("javax.inject", "Inject")
    val MODIFIER =  ClassName("androidx.compose.ui", "Modifier")
    val MUTABLE_STATE_FLOW = ClassName("kotlinx.coroutines.flow", "MutableStateFlow")
    val NAVIGATOR = ClassName("com.slack.circuit.runtime", "Navigator")
    val PARCELIZE = ClassName("kotlinx.parcelize", "Parcelize")
    val PRESENTER = ClassName("com.slack.circuit.runtime.presenter", "Presenter")
    val SCREEN_INTERFACE = ClassName("com.slack.circuit.runtime.screen", "Screen")
    val SLACK_DISPATCHER = ClassName("slack.foundation.coroutines", "SlackDispatchers")
    val STATE_FLOW = ClassName("kotlinx.coroutines.flow", "StateFlow")
    val USER_SCOPE = ClassName("slack.di", "UserScope")
    val UDF_VIEW_MODEL = ClassName("slack.coreui.viewmodel", "UdfViewModel")
    val VIEW_MODEL = ClassName("androidx.lifecycle", "ViewModel")
    val VIEW_MODEL_KEY = ClassName("slack.coreui.di.presenter", "ViewModelKey")
  }
}