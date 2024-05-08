package slack.tooling.projectgen.circuitgen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import com.slack.circuit.runtime.presenter.Presenter
import com.squareup.kotlinpoet.ClassName
import slack.tooling.projectgen.CheckboxElement
import slack.tooling.projectgen.DividerElement
import slack.tooling.projectgen.SectionElement
import slack.tooling.projectgen.TextElement
import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.Companion.APP_SCOPE
import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.Companion.USER_SCOPE
import java.nio.file.Path


internal class CircuitGenPresenter(
  private val directory: Path,
  private val packageName: String?,
  private val baseTestClass: Map<String, String?>,
  private val fileGenerationListener: FileGenerationListener,
  private val onDismissDialog: () -> Unit,
  private val generationMode: GenerationMode
): Presenter<CircuitGenScreen.State> {

  private val featureName = TextElement("", "Feature Name")
  private val circuitUi = CheckboxElement(false, name = "UI", hint = "Generate UI class", indentLevel = 10)
  private val circuitPresenter = CheckboxElement(false, name = "Presenter", hint = "Generate Presenter class", indentLevel = 10)
  private val assistedInjection = SectionElement("Assisted Injection", "(Optional)", indentLevel = 10)
  private val assistedScreen = CheckboxElement(false, name = "Screen", hint = "Add @assisted for screen parameter", indentLevel = 30)
  private val assistedNavigator = CheckboxElement(false, name = "Navigator", hint = "Add @assisted for navigator parameter", indentLevel = 30)
  private val circuitInject = SectionElement("Circuit Injection", "(Optional)", indentLevel = 10)
  private val userScopeCircuitInject = CheckboxElement(false, name = "User Scope", hint = "Add UserScope to Circuit Inject", indentLevel = 30)
  private val appScopeCircuitInject = CheckboxElement(false, name = "App Scope", hint = "Add AppScope to Circuit Inject", indentLevel = 30)
  private val test = CheckboxElement(false, name = "Tests", hint = "Should generate test class", indentLevel = 10)

  private val screenAndPresenterElements = mutableStateListOf(
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
  private val viewModelElements = mutableStateListOf(
    featureName,
    DividerElement,
    circuitInject,
    userScopeCircuitInject,
    appScopeCircuitInject,
  )

  @Composable
  override fun present(): CircuitGenScreen.State {
    val uiElements = when (generationMode) {
      GenerationMode.ViewModel -> {
        circuitInject.indentLevel = 0
        userScopeCircuitInject.indentLevel = 10
        appScopeCircuitInject.indentLevel = 10
        viewModelElements
      }
      GenerationMode.ScreenAndPresenter -> {
        circuitInject.isVisible = circuitPresenter.isChecked
        assistedInjection.isVisible = circuitPresenter.isChecked
        assistedScreen.isVisible = circuitPresenter.isChecked
        assistedNavigator.isVisible = circuitPresenter.isChecked
        userScopeCircuitInject.isVisible = circuitPresenter.isChecked
        appScopeCircuitInject.isVisible = circuitPresenter.isChecked
        screenAndPresenterElements
      }
    }

    return CircuitGenScreen.State(
      uiElements = uiElements,
    ) { event ->
      when (event) {
        CircuitGenScreen.Event.Generate -> {
          when (generationMode) {
            GenerationMode.ViewModel -> {
              generateViewModel(
                featureName.value,
                userScopeCircuitInject.isChecked,
                appScopeCircuitInject.isChecked,
              )
            }

            GenerationMode.ScreenAndPresenter -> {
              generateScreenAndPresenter(
                featureName.value,
                circuitPresenter.isChecked,
                circuitUi.isChecked,
                assistedScreen.isChecked,
                assistedNavigator.isChecked,
                userScopeCircuitInject.isChecked,
                appScopeCircuitInject.isChecked,
                test.isChecked,
              )
            }
          }
          onDismissDialog()
        }
      }
    }
  }

  private fun generateViewModel(
    feature: String,
    userScopeInject: Boolean,
    appScopeInject: Boolean
  ) {
    val additionalScope = mutableSetOf<ClassName>().apply {
      if (userScopeInject) {
        add(USER_SCOPE)
      }
      if (appScopeInject) {
        add(APP_SCOPE)
      }
    }
    val components = mutableListOf(CircuitViewModelScreen(), CircuitViewModel(additionalScope))
    components.forEach { component ->
      component.writeToFile(directory, packageName, feature)
    }

    fileGenerationListener.onFilesGenerated("${directory}/${CircuitViewModelScreen().screenClassName(feature)}.kt")
  }

  private fun generateScreenAndPresenter(
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
      val additionalCircuitInject = mutableSetOf<ClassName>().apply {
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
      val additionalCircuitInject = mutableSetOf<ClassName>().apply {
        if (userScopeInject) add(USER_SCOPE)
        if (appScopeInject) add(APP_SCOPE)
      }
      val noUi = circuitUi.not()
      components.add(CircuitPresenter(assistedInjection = AssistedInjectionConfig(screen = assistedScreen, navigator = assistedNavigator), additionalCircuitInject = additionalCircuitInject, noUi = noUi))
      if (generateTest) {
        components.add(CircuitTest("PresenterTest", baseTestClass["PresenterTest"]))
      }
    }

    components.forEach { component ->
      component.writeToFile(directory, packageName, feature)
    }

    fileGenerationListener.onFilesGenerated("${directory}/${components.first().screenClassName(feature)}.kt")
  }
}

sealed class GenerationMode {
  object ViewModel : GenerationMode()
  object ScreenAndPresenter : GenerationMode()
}

interface FileGenerationListener {
  fun onFilesGenerated(fileNames: String)
}