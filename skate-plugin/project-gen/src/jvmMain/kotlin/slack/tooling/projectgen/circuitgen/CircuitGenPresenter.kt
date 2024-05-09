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
import androidx.compose.runtime.mutableStateListOf
import com.slack.circuit.runtime.presenter.Presenter
import com.squareup.kotlinpoet.ClassName
import java.nio.file.Path
import slack.tooling.projectgen.CheckboxElement
import slack.tooling.projectgen.DividerElement
import slack.tooling.projectgen.SectionElement
import slack.tooling.projectgen.TextElement
import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.Companion.APP_SCOPE
import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.Companion.USER_SCOPE

internal class CircuitGenPresenter(
  private val selectedDir: Path,
  private val baseTestClass: Map<String, String?>,
  private val fileGenerationListener: FileGenerationListener,
  private val onDismissDialog: () -> Unit,
) : Presenter<CircuitGenScreen.State> {

  private val featureName = TextElement("", "Feature Name")
  private val circuitUi =
    CheckboxElement(false, name = "UI", hint = "Generate UI class", indentLevel = 10)
  private val circuitPresenter =
    CheckboxElement(false, name = "Presenter", hint = "Generate Presenter class", indentLevel = 10)
  private val assistedInjection =
    SectionElement("Assisted Injection", "(Optional)", indentLevel = 10)
  private val assistedScreen =
    CheckboxElement(
      false,
      name = "Screen",
      hint = "Add @assisted for screen parameter",
      indentLevel = 30,
    )
  private val assistedNavigator =
    CheckboxElement(
      false,
      name = "Navigator",
      hint = "Add @assisted for navigator parameter",
      indentLevel = 30,
    )
  private val circuitInject = SectionElement("Circuit Injection", "(Optional)", indentLevel = 10)
  private val userScopeCircuitInject =
    CheckboxElement(
      false,
      name = "User Scope",
      hint = "Add UserScope to Circuit Inject",
      indentLevel = 30,
    )
  private val appScopeCircuitInject =
    CheckboxElement(
      false,
      name = "App Scope",
      hint = "Add AppScope to Circuit Inject",
      indentLevel = 30,
    )
  private val test =
    CheckboxElement(false, name = "Tests", hint = "Should generate test class", indentLevel = 10)

  private val uiElements =
    mutableStateListOf(
      featureName,
      DividerElement,
      SectionElement("Class(es) to generate", ""),
      circuitUi,
      circuitPresenter,
      assistedInjection,
      assistedScreen,
      assistedNavigator,
      circuitInject,
      userScopeCircuitInject,
      appScopeCircuitInject,
      test,
    )

  @Composable
  override fun present(): CircuitGenScreen.State {
    circuitInject.isVisible = circuitPresenter.isChecked
    assistedInjection.isVisible = circuitPresenter.isChecked
    assistedScreen.isVisible = circuitPresenter.isChecked
    assistedNavigator.isVisible = circuitPresenter.isChecked
    userScopeCircuitInject.isVisible = circuitPresenter.isChecked
    appScopeCircuitInject.isVisible = circuitPresenter.isChecked

    return CircuitGenScreen.State(uiElements = uiElements) { event ->
      when (event) {
        CircuitGenScreen.Event.Generate -> {
          generateCircuitFeature(
            featureName.value,
            circuitPresenter.isChecked,
            circuitUi.isChecked,
            assistedScreen.isChecked,
            assistedNavigator.isChecked,
            userScopeCircuitInject.isChecked,
            appScopeCircuitInject.isChecked,
            test.isChecked,
          )
          onDismissDialog()
        }
      }
    }
  }

  private fun generateCircuitFeature(
    feature: String,
    circuitPresenter: Boolean,
    circuitUi: Boolean,
    assistedScreen: Boolean,
    assistedNavigator: Boolean,
    userScopeInject: Boolean,
    appScopeInject: Boolean,
    generateTest: Boolean,
  ) {
    val components = mutableListOf<CircuitComponent>()

    if (circuitUi) {
      val additionalCircuitInject =
        mutableListOf<ClassName>().apply {
          if (userScopeInject) add(USER_SCOPE)
          if (appScopeInject) add(APP_SCOPE)
        }
      components.add(CircuitUiFeature(additionalCircuitInject))
      if (generateTest) {
        components.add(CircuitTest("UiTest", baseTestClass["UiTest"]))
      }
    }

    if (circuitPresenter) {
      components.add(CircuitScreen())
      val additionalCircuitInject =
        mutableListOf<ClassName>().apply {
          if (userScopeInject) add(USER_SCOPE)
          if (appScopeInject) add(APP_SCOPE)
        }
      val noUi = circuitUi.not()
      components.add(
        CircuitPresenter(
          assistedInjection =
            AssistedInjectionConfig(screen = assistedScreen, navigator = assistedNavigator),
          additionalCircuitInject = additionalCircuitInject,
          noUi = noUi,
        )
      )
      if (generateTest) {
        components.add(CircuitTest("PresenterTest", baseTestClass["PresenterTest"]))
      }
    }

    components.forEach { component -> component.writeToFile(selectedDir, feature) }

    fileGenerationListener.onFilesGenerated(
      "${selectedDir}/${components.first().screenClassName(feature)}.kt"
    )
  }
}

interface FileGenerationListener {
  fun onFilesGenerated(fileNames: String)
}
