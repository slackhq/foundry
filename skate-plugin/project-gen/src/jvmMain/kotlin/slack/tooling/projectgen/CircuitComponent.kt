package slack.tooling.projectgen

import java.io.File

interface CircuitComponent {
  val fileSuffix: String
  fun generate(packageName: String?, className: String): String

  fun isTestComponent(): Boolean = false

  fun writeToFile(directory: String, packageName: String?, className: String) {
    val content = generate(packageName, className)
    File(directory).apply {
      if (!exists()) {
        mkdirs()
      }
    }
    File("$directory/${className}${fileSuffix}.kt").writeText(content)
  }
}

class CircuitScreen: CircuitComponent {
  override val fileSuffix = "Screen"

  override fun generate(packageName: String?, className: String): String {
    val packageLine = if (!packageName.isNullOrEmpty()) "package $packageName\n" else ""
    return """
      $packageLine
      import androidx.compose.runtime.Immutable
      import com.slack.circuit.runtime.CircuitUiEvent
      import com.slack.circuit.runtime.CircuitUiState
      import com.slack.circuit.runtime.screen.Screen
      import kotlinx.parcelize.Parcelize
      
      @Parcelize
      class ${className}Screen : Screen {
      
        data class State(
          val message: String = "",
          val eventSink: (Event) -> Unit = {}
        ) : CircuitUiState
      
        @Immutable
        sealed interface Event : CircuitUiEvent {
          data object UserTappedText : Event
        }
      }
      """.trimIndent()
  }
}

class CircuitPresenter: CircuitComponent{
  override val fileSuffix = "Presenter"
  override fun generate(packageName: String?, className: String): String {
    val packageLine = if (!packageName.isNullOrEmpty()) "package $packageName\n" else ""
    return """
      $packageLine
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.getValue
      import androidx.compose.runtime.mutableIntStateOf
      import androidx.compose.runtime.saveable.rememberSaveable
      import androidx.compose.runtime.setValue
      import com.slack.circuit.codegen.annotations.CircuitInject
      import com.slack.circuit.runtime.Navigator
      import com.slack.circuit.runtime.presenter.Presenter
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      import slack.di.UserScope
      import slack.libraries.foundation.compose.rememberStableCoroutineScope
      
      class ${className}Presenter
      @AssistedInject
      constructor(
        @Assisted private val screen: ${className}Screen,
        @Assisted private val navigator: Navigator,
      ) : Presenter<${className}Screen.State> {
      
        @Composable
        override fun present(): ${className}Screen.State {
          val scope = rememberStableCoroutineScope()
          var tapCounter by rememberSaveable { mutableIntStateOf(0) }
          
          return ${className}Screen.State(
            message = tapCounter.toString()
          ) { event ->
            when (event) {
              is ${className}Screen.Event.UserTappedText -> tapCounter++
            }
          }
        }
        
        @CircuitInject(${className}Screen::class, UserScope::class)
        @AssistedFactory
        interface Factory {
          fun create(screen: ${className}Screen, navigator: Navigator): ${className}Presenter
        }
      }
      """.trimIndent()
  }
}

class CircuitPresenterTest: CircuitComponent {
  override val fileSuffix = "PresenterTest"

  override fun isTestComponent(): Boolean = true
  override fun generate(packageName: String?, className: String): String {
    val packageLine = if (!packageName.isNullOrEmpty()) "package $packageName\n" else ""
    return """
      $packageLine
      import com.slack.circuit.test.FakeNavigator
      import com.slack.circuit.test.test
      import kotlinx.coroutines.test.runTest
      import org.junit.Assert.assertEquals
      import org.junit.Rule
      import org.junit.Test
      import slack.foundation.coroutines.test.CoroutinesRule
      import slack.test.SlackJvmTest

      class ${className}PresenterTest : SlackJvmTest() {
        @get:Rule
        val coroutineRule = CoroutinesRule()

        private val screen = ${className}Screen()
        private val navigator = FakeNavigator(screen)

        private val presenter = ${className}Presenter(
          screen = screen,
          navigator = navigator
        )

        @Test
        fun `User taps text - tap counter is incremented`() = runTest {
          presenter.test {
            // Initial state
            var state = awaitItem()

            assertEquals(state.message, "0")

            state.eventSink(${className}Screen.Event.UserTappedText)
            state = awaitItem()

            assertEquals(state.message, "1")

            cancelAndIgnoreRemainingEvents()
          }
        }
      }

    """.trimIndent()
  }
}

class CircuitUiFeature: CircuitComponent {
  override val fileSuffix = ""
  override fun generate(packageName: String?, className: String): String {
    val packageLine = if (!packageName.isNullOrEmpty()) "package $packageName\n" else ""
    return """
      $packageLine
      import androidx.compose.foundation.clickable
      import androidx.compose.material3.Text
      import androidx.compose.runtime.Composable
      import androidx.compose.ui.Modifier
      import com.slack.circuit.codegen.annotations.CircuitInject
      import slack.di.UserScope

      @CircuitInject(${className}Screen::class, UserScope::class)
      @Composable
      fun ${className}(state: ${className}Screen.State, modifier: Modifier = Modifier) {
        Text(
          text = state.message,
          modifier = modifier.clickable { state.eventSink(${className}Screen.Event.UserTappedText) }
        )
      }
    """.trimIndent()
  }
}

class CircuitUiTest: CircuitComponent {
  override val fileSuffix = "UiTest"
  override fun isTestComponent(): Boolean = true
  override fun generate(packageName: String?, className: String): String {
    val packageLine = if (!packageName.isNullOrEmpty()) "package $packageName\n" else ""
    return """
      $packageLine
      import androidx.compose.ui.test.assertIsDisplayed
      import androidx.compose.ui.test.hasClickAction
      import androidx.compose.ui.test.hasText
      import androidx.compose.ui.test.junit4.createComposeRule
      import androidx.compose.ui.test.onNodeWithText
      import androidx.compose.ui.test.performClick
      import kotlin.test.assertEquals
      import org.junit.Rule
      import org.junit.Test
      import org.junit.runner.RunWith
      import slack.test.android.SlackAndroidJvmTest

      class ${className}UiTest : SlackAndroidJvmTest() {

        @get:Rule
        val composeTestRule = createComposeRule()

        @Test
        fun `Clicking text emits tap event`() {
          with(composeTestRule) {
            var lastEvent: ${className}Screen.Event? = null
            val uiState = ${className}Screen.State(message = "Test message") {
              lastEvent = it
            }

            setContent {
              ${className}(state = uiState)
            }

            onNodeWithText("Test message").assertIsDisplayed()
            onNode(
              hasText("Test message")
                .and(hasClickAction())
            )
              .performClick()

            assertEquals(${className}Screen.Event.UserTappedText, lastEvent)
          }
        }
      }

    """.trimIndent()
  }
}

class CircuitViewModel: CircuitComponent {
  override val fileSuffix = "Presenter"
  override fun generate(packageName: String?, className: String): String {
    val packageLine = if (!packageName.isNullOrEmpty()) "package $packageName\n" else ""
    return """
      $packageLine
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.getValue
      import androidx.compose.runtime.mutableIntStateOf
      import androidx.compose.runtime.saveable.rememberSaveable
      import androidx.compose.runtime.setValue
      import com.slack.circuit.runtime.Navigator
      import com.slack.circuit.runtime.presenter.Presenter
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      import slack.di.UserScope
      import slack.libraries.foundation.compose.rememberStableCoroutineScope

      /** 
       * TODO (remove): This Circuit [Presenter] was generated without UI or [CircuitInject], and can be
       * used with legacy views via `by circuitState()` in your Fragment or Activity.
       */
      class ${className}Presenter
      @AssistedInject
      constructor(
        @Assisted private val screen: ${className}Screen,
        @Assisted private val navigator: Navigator,
      ) : Presenter<${className}Screen.State> {

        @Composable
        override fun present(): ${className}Screen.State {
          val scope = rememberStableCoroutineScope()
          var tapCounter by rememberSaveable { mutableIntStateOf(0) }
          
          return ${className}Screen.State(
            message = tapCounter.toString()
          ) { event ->
            when (event) {
              is ${className}Screen.Event.UserTappedText -> tapCounter++
            }
          }
        }

        @AssistedFactory
        interface Factory {
          fun create(screen: ${className}Screen, navigator: Navigator): ${className}Presenter
        }
      }
    """.trimIndent()
  }
}

class FakeCircuitComponent(private val isTest: Boolean): CircuitComponent {
  override val fileSuffix: String = "Fake"
  var directoryCreated: String? = null
  override fun isTestComponent(): Boolean = isTest
  override fun generate(packageName: String?, className: String): String{
    val packageLine = if (!packageName.isNullOrEmpty()) "package $packageName\n" else ""
    return """
      $packageLine
      class ${className}Fake
    """.trimIndent()
  }
  override fun writeToFile(directory: String, packageName: String?, className: String) {
    directoryCreated = directory
  }
}
