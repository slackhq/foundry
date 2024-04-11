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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import java.io.File
import java.io.StringWriter

interface CircuitComponent {
  val fileSuffix: String

  fun generate(packageName: String?, className: String): FileSpec

  fun isTestComponent(): Boolean = false

  fun writeToFile(directory: String, packageName: String?, className: String) {
    val fileSpec = generate(packageName, className)
    File(directory).apply {
      if (!exists()) {
        mkdirs()
      }
    }
    val stringWriter = StringWriter()
    fileSpec.writeTo(stringWriter)
    val generatedCode = stringWriter.toString().replace("public ", "")
    File("$directory/${className}${fileSuffix}.kt").writeText(generatedCode)
  }
}

class CircuitScreen : CircuitComponent {
  override val fileSuffix = "Screen"

  override fun generate(packageName: String?, className: String): FileSpec {
    val packageLine = packageName.orEmpty()
    val circuitScreenClass = "${className}Screen"
    val parcelize = ClassName("kotlinx.parcelize", "Parcelize")
    val circuitUiEvent = ClassName("com.slack.circuit.runtime", "CircuitUiEvent")
    val circuitUiState = ClassName("com.slack.circuit.runtime", "CircuitUiState")
    val screenInterface = ClassName("com.slack.circuit.runtime.screen", "Screen")
    val eventInterface =
      TypeSpec.interfaceBuilder("Event")
        .addAnnotation(Immutable::class)
        .addModifiers(KModifier.SEALED)
        .addSuperinterface(circuitUiEvent)
        .build()

    val eventInterfaceName = eventInterface.name?.let { ClassName("", it) }
    val stateClass = TypeSpec.classBuilder("State")
      .addModifiers(KModifier.DATA)
      .primaryConstructor(
        FunSpec.constructorBuilder()
          .addParameter(
            ParameterSpec.builder("message", String::class).defaultValue("%S", "").build()
          )
          .addParameter(
            ParameterSpec.builder("eventSink",
              LambdaTypeName.get(
                receiver = eventInterfaceName, returnType = Unit::class.asTypeName())).defaultValue("{}").build()
          )
          .build())
      .addProperty(
        PropertySpec.builder("message", String::class)
          .initializer("message")
          .build()
      )
      .addProperty(PropertySpec.builder("eventSink", LambdaTypeName.get(receiver = eventInterfaceName, returnType = Unit::class.asTypeName()))
        .initializer("eventSink").build())
      .addSuperinterface(circuitUiState)
      .build()

    return FileSpec.builder(packageLine, circuitScreenClass)
        .addType(TypeSpec.classBuilder(circuitScreenClass)
          .addSuperinterface(screenInterface)
          .addAnnotation(parcelize)
          .addType(stateClass)
          .addType(eventInterface)
          .build())
        .build()

//    """
//      $packageLine
//      import androidx.compose.runtime.Immutable
//      import com.slack.circuit.runtime.CircuitUiEvent
//      import com.slack.circuit.runtime.CircuitUiState
//      import com.slack.circuit.runtime.screen.Screen
//      import kotlinx.parcelize.Parcelize
//
//      @Parcelize
//      class ${className}Screen : Screen {
//
//        data class State(
//          val message: String = "",
//          val eventSink: (Event) -> Unit = {}
//        ) : CircuitUiState
//
//        @Immutable
//        sealed interface Event : CircuitUiEvent {
//          data object UserTappedText : Event
//        }
//      }
//      """
//      .trimIndent()
  }
}

class CircuitPresenter(private val useAssistedInjection: Boolean = true, private val noUi: Boolean = false) : CircuitComponent {
  override val fileSuffix = "Presenter"

  override fun generate(packageName: String?, className: String): FileSpec {
    val packageLine = packageName.orEmpty()
    val screenClassName = "${className}Screen"
    val presenterClassName = "${className}Presenter"
    val assistedInject = ClassName("dagger.assisted", "AssistedInject")
    val assisted = ClassName("dagger.assisted", "Assisted")
    val assistedFactory = ClassName("dagger.assisted", "AssistedFactory")

    val factoryBuilder = TypeSpec.interfaceBuilder("Factory")
      .apply {
        if (useAssistedInjection) {
          addAnnotation(assistedFactory)
        }
      }

    val constructorBuilder = FunSpec.constructorBuilder()
      .apply {
        if (useAssistedInjection) {
          addAnnotation(assistedInject)
        }
          addParameter(
            ParameterSpec.builder(
              "screen", ClassName("", screenClassName)
            ).apply {
              if (useAssistedInjection) {
                addAnnotation(assisted)
              }
            }
              .build()
          )
          addParameter(
            ParameterSpec.builder(
              "navigator", ClassName("com.slack.circuit.runtime", "Navigator")
            )
              .addAnnotation(assisted)
              .build()
          )
      }

    val presenterClass = TypeSpec.classBuilder(presenterClassName)
      .apply {
        if (noUi) {
          addKdoc("""
         TODO (remove): This Circuit [Presenter] was generated without UI or [CircuitInject], and can be
         used with legacy views via `by circuitState()` in your Fragment or Activity.
      """.trimIndent())
        }
          primaryConstructor(constructorBuilder.build())
          .addSuperinterface(ClassName("com.slack.circuit.runtime.presenter", "Presenter").parameterizedBy(ClassName("", "${className}Screen.State")))
          .addProperty(PropertySpec.builder("screen", ClassName("", "${className}Screen")).addModifiers(KModifier.PRIVATE).initializer("screen").build())
          .addProperty(PropertySpec.builder("navigator", ClassName("com.slack.circuit.runtime", "Navigator")).addModifiers(KModifier.PRIVATE).initializer("navigator").build())
          .addFunction(
            FunSpec.builder("present")
              .addModifiers(KModifier.OVERRIDE)
              .addAnnotation(ClassName("androidx.compose.runtime", "Composable"))
              .returns(ClassName("", "${className}Screen.State"))
              .addCode(
                """
              val scope = rememberStableCoroutineScope()
              return ${className}Screen.State() { event -> 
              }
            """.trimIndent()
              )
              .build()
          ).addType(
            factoryBuilder
              .addAnnotation(AnnotationSpec.builder(ClassName("com.slack.circuit.codegen.annotations", "CircuitInject"))
                .addMember("%T::class", ClassName("", "${className}Screen"))
                .addMember("%T::class", ClassName("slack.di", "UserScope"))
                .build())
              .addFunction(
                FunSpec.builder("create")
                  .addParameter("screen", ClassName("", "${className}Screen"))
                  .addParameter("navigator", ClassName("com.slack.circuit.runtime", "Navigator"))
                  .returns(ClassName("", "${className}Presenter"))
                  .build()
              )
              .build()
          )
      }

    return FileSpec.builder(packageLine, presenterClassName)
        .addType(presenterClass.build())
        .build()


//      $packageLine
//      import androidx.compose.runtime.Composable
//      import androidx.compose.runtime.getValue
//      import androidx.compose.runtime.mutableIntStateOf
//      import androidx.compose.runtime.saveable.rememberSaveable
//      import androidx.compose.runtime.setValue
//      import com.slack.circuit.codegen.annotations.CircuitInject
//      import com.slack.circuit.runtime.Navigator
//      import com.slack.circuit.runtime.presenter.Presenter
//      import dagger.assisted.Assisted
//      import dagger.assisted.AssistedFactory
//      import dagger.assisted.AssistedInject
//      import slack.di.UserScope
//      import slack.libraries.foundation.compose.rememberStableCoroutineScope
//
//      class ${className}Presenter
//      @AssistedInject
//      constructor(
//        @Assisted private val screen: ${className}Screen,
//        @Assisted private val navigator: Navigator,
//      ) : Presenter<${className}Screen.State> {
//
//        @Composable
//        override fun present(): ${className}Screen.State {
//          val scope = rememberStableCoroutineScope()
//          var tapCounter by rememberSaveable { mutableIntStateOf(0) }
//
//          return ${className}Screen.State(
//            message = tapCounter.toString()
//          ) { event ->
//            when (event) {
//              is ${className}Screen.Event.UserTappedText -> tapCounter++
//            }
//          }
//        }
//
//        @CircuitInject(${className}Screen::class, UserScope::class)
//        @AssistedFactory
//        interface Factory {
//          fun create(screen: ${className}Screen, navigator: Navigator): ${className}Presenter
//        }
//      }
//      """
//      .trimIndent()
  }
}

class CircuitPresenterTest : CircuitComponent {
  override val fileSuffix = "PresenterTest"

  override fun isTestComponent(): Boolean = true

  override fun generate(packageName: String?, className: String): FileSpec {
    val packageLine = if (!packageName.isNullOrEmpty()) packageName else ""
    val testClassSpec = TypeSpec.classBuilder("${className}PresenterTest")
      .superclass(ClassName("slack.test", "SlackJvmTest"))
      .build()

    return FileSpec.builder(packageLine, "${className}PresenterTest")
      .addType(testClassSpec)
      .build()
//
//    return """
//      $packageLine
//      import com.slack.circuit.test.FakeNavigator
//      import com.slack.circuit.test.test
//      import kotlinx.coroutines.test.runTest
//      import org.junit.Assert.assertEquals
//      import org.junit.Rule
//      import org.junit.Test
//      import slack.foundation.coroutines.test.CoroutinesRule
//      import slack.test.SlackJvmTest
//
//      class ${className}PresenterTest : SlackJvmTest() {
//        @get:Rule
//        val coroutineRule = CoroutinesRule()
//
//        private val screen = ${className}Screen()
//        private val navigator = FakeNavigator(screen)
//
//        private val presenter = ${className}Presenter(
//          screen = screen,
//          navigator = navigator
//        )
//
//        @Test
//        fun `User taps text - tap counter is incremented`() = runTest {
//          presenter.test {
//            // Initial state
//            var state = awaitItem()
//
//            assertEquals(state.message, "0")
//
//            state.eventSink(${className}Screen.Event.UserTappedText)
//            state = awaitItem()
//
//            assertEquals(state.message, "1")
//
//            cancelAndIgnoreRemainingEvents()
//          }
//        }
//      }
//
//    """
//      .trimIndent()
  }
}

class CircuitUiFeature : CircuitComponent {
  override val fileSuffix = ""

  override fun generate(packageName: String?, className: String): FileSpec {
    val packageLine = if (!packageName.isNullOrEmpty()) packageName else ""
    val uiFunction = FunSpec.builder(className)
      .addAnnotation(AnnotationSpec.builder(ClassName("com.slack.circuit.codegen.annotations", "CircuitInject"))
        .addMember("%T::class", ClassName(packageLine, "${className}Screen"))
        .addMember("%T::class", ClassName("slack.di", "UserScope"))
        .build())
      .addAnnotation(Composable::class)
      .addParameter(
        ParameterSpec.builder("state", ClassName(packageLine, "${className}Screen.State")).build()
      ).addParameter(
        ParameterSpec.builder("modifier", ClassName("androidx.compose.ui", "Modifier")).defaultValue("Modifier").build()
      )
      .build()

    return FileSpec.builder(packageLine, className)
      .addFunction(uiFunction)
      .build()

//    return """
//      $packageLine
//      import androidx.compose.foundation.clickable
//      import androidx.compose.material3.Text
//      import androidx.compose.runtime.Composable
//      import androidx.compose.ui.Modifier
//      import com.slack.circuit.codegen.annotations.CircuitInject
//      import slack.di.UserScope
//
//      @CircuitInject(${className}Screen::class, UserScope::class)
//      @Composable
//      fun ${className}(state: ${className}Screen.State, modifier: Modifier = Modifier) {
//        Text(
//          text = state.message,
//          modifier = modifier.clickable { state.eventSink(${className}Screen.Event.UserTappedText) }
//        )
//      }
//    """
//      .trimIndent()
  }
}

class CircuitUiTest : CircuitComponent {
  override val fileSuffix = "UiTest"

  override fun isTestComponent(): Boolean = true

  override fun generate(packageName: String?, className: String): FileSpec {
    val packageLine = if (!packageName.isNullOrEmpty()) packageName else ""
    val testClassSpec = TypeSpec.classBuilder("${className}UiTest")
      .superclass(ClassName("slack.test.android", "SlackAndroidJvmTest"))
      .build()

    return FileSpec.builder(packageLine, "${className}UiTest")
      .addType(testClassSpec)
      .build()
  }

//  override fun generate(packageName: String?, className: String): String {
//    val packageLine = if (!packageName.isNullOrEmpty()) "package $packageName\n" else ""
//    return """
//      $packageLine
//      import androidx.compose.ui.test.assertIsDisplayed
//      import androidx.compose.ui.test.hasClickAction
//      import androidx.compose.ui.test.hasText
//      import androidx.compose.ui.test.junit4.createComposeRule
//      import androidx.compose.ui.test.onNodeWithText
//      import androidx.compose.ui.test.performClick
//      import kotlin.test.assertEquals
//      import org.junit.Rule
//      import org.junit.Test
//      import org.junit.runner.RunWith
//      import slack.test.android.SlackAndroidJvmTest
//
//      class ${className}UiTest : SlackAndroidJvmTest() {
//
//        @get:Rule
//        val composeTestRule = createComposeRule()
//
//        @Test
//        fun `Clicking text emits tap event`() {
//          with(composeTestRule) {
//            var lastEvent: ${className}Screen.Event? = null
//            val uiState = ${className}Screen.State(message = "Test message") {
//              lastEvent = it
//            }
//
//            setContent {
//              ${className}(state = uiState)
//            }
//
//            onNodeWithText("Test message").assertIsDisplayed()
//            onNode(
//              hasText("Test message")
//                .and(hasClickAction())
//            )
//              .performClick()
//
//            assertEquals(${className}Screen.Event.UserTappedText, lastEvent)
//          }
//        }
//      }
//
//    """
//      .trimIndent()
//  }
  }

  class FakeCircuitComponent(private val isTest: Boolean) : CircuitComponent {
    override val fileSuffix: String = "Fake"
    var directoryCreated: String? = null

    override fun isTestComponent(): Boolean = isTest

    override fun generate(packageName: String?, className: String): FileSpec {
      val packageLine = packageName.orEmpty()
      return FileSpec.builder(packageLine, "${className}FakeTest")
        .build()
    }

    override fun writeToFile(directory: String, packageName: String?, className: String) {
      directoryCreated = directory
    }
  }

