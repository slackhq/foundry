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
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import java.io.File
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.pathString
import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.Companion.CIRCUIT_INJECT
import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.Companion.CIRCUIT_UI_EVENT
import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.Companion.CIRCUIT_UI_STATE
import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.Companion.COMPOSABLE
import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.Companion.IMMUTABLE
import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.Companion.JAVA_INJECT
import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.Companion.MODIFIER
import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.Companion.MUTABLE_STATE_FLOW
import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.Companion.NAVIGATOR
import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.Companion.PARCELIZE
import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.Companion.PRESENTER
import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.Companion.SCREEN_INTERFACE
import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.Companion.SLACK_DISPATCHER
import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.Companion.STATE_FLOW
import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.Companion.UDF_VIEW_MODEL
import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.Companion.VIEW_MODEL
import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.Companion.VIEW_MODEL_KEY

interface CircuitComponent {
  val fileSuffix: String

  fun screenClassName(featureName: String): String = "${featureName.replace(fileSuffix, "")}Screen"

  fun generate(packageName: String, className: String): FileSpec

  fun isTestComponent(): Boolean = false

  fun writeToFile(directory: Path, packageName: String?, className: String) {
    val baseDirectory =
      if (isTestComponent()) Path.of(directory.pathString.replace("src/main", "src/test"))
      else directory
    val fileSpec = generate(packageName.orEmpty(), className)
    Files.createDirectories(baseDirectory)
    val stringWriter = StringWriter()
    fileSpec.writeTo(stringWriter)
    val generatedCode = stringWriter.toString().replace("public ", "")
    File("${baseDirectory}/${className}${fileSuffix}.kt").writeText(generatedCode)
  }
}

class CircuitScreen : CircuitComponent {
  override val fileSuffix = "Screen"

  override fun generate(packageName: String, className: String): FileSpec {
    /**
     * Generate Circuit Screen class, eg:
     *
     * @Parcelize class ${NAME}Screen : Screen { data class State( val eventSink: Event.() -> Unit =
     *   {}, ) : CircuitUiState
     * @Immutable sealed interface Event : CircuitUiEvent }
     */
    val screenClassName = ClassName(packageName, screenClassName(className))
    val eventInterfaceCN = screenClassName.nestedClass("Event")
    val eventInterface =
      TypeSpec.interfaceBuilder(eventInterfaceCN.simpleName)
        .addAnnotation(IMMUTABLE)
        .addModifiers(KModifier.SEALED)
        .addSuperinterface(CIRCUIT_UI_EVENT)
        .build()

    val stateClass =
      TypeSpec.classBuilder("State")
        .addModifiers(KModifier.DATA)
        .primaryConstructor(
          FunSpec.constructorBuilder()
            .addParameter(
              ParameterSpec.builder(
                  "eventSink",
                  LambdaTypeName.get(
                    receiver = eventInterfaceCN,
                    returnType = Unit::class.asTypeName(),
                  ),
                )
                .defaultValue("{}")
                .build()
            )
            .build()
        )
        .addProperty(
          PropertySpec.builder(
              "eventSink",
              LambdaTypeName.get(receiver = eventInterfaceCN, returnType = Unit::class.asTypeName()),
            )
            .initializer("eventSink")
            .build()
        )
        .addSuperinterface(CIRCUIT_UI_STATE)
        .build()
    val typeSpec =
      TypeSpec.classBuilder(screenClassName)
        .addSuperinterface(SCREEN_INTERFACE)
        .addAnnotation(PARCELIZE)
        .addType(stateClass)
        .addType(eventInterface)
        .build()
    return FileSpec.get(packageName, typeSpec)
  }
}

class CircuitPresenter(
  private val assistedInjection: AssistedInjectionConfig,
  private val additionalCircuitInject: Set<ClassName> = setOf(),
  private val noUi: Boolean = false,
) : CircuitComponent {
  override val fileSuffix = "Presenter"

  override fun generate(packageName: String, className: String): FileSpec {
    /**
     * Generate Circuit Presenter class, eg: class ${NAME}Presenter @AssistedInject constructor(
     *
     * @Assisted private val screen: ${NAME}Screen,
     * @Assisted private val navigator: Navigator, ) : Presenter<${NAMEScreen.State> {
     * @Composable override fun present(): FeatureScreen.State { } }
     * @AssistedFactory
     * @CircuitInject( ${NAME}Screen::class, UserScope::class, ) fun interface Factory { fun
     *   create(screen: ${NAME}Screen, navigator: Navigator): ${NAME}Presenter { } } }
     */
    val screenClass = ClassName(packageName, screenClassName(className))
    val assistedInject = ClassName("dagger.assisted", "AssistedInject")
    val assisted = ClassName("dagger.assisted", "Assisted")
    val assistedFactory = ClassName("dagger.assisted", "AssistedFactory")
    val addAssistedInjection = assistedInjection.screen || assistedInjection.navigator

    val factoryBuilder =
      TypeSpec.funInterfaceBuilder("Factory").apply {
        if (addAssistedInjection) {
          addAnnotation(assistedFactory)
        }
        addAnnotation(
          AnnotationSpec.builder(CIRCUIT_INJECT)
            .apply {
              addMember("%T::class", screenClass)
              additionalCircuitInject.forEach { inject -> addMember("%T::class", inject) }
            }
            .build()
        )
        addFunction(
          FunSpec.builder("create")
            .addModifiers(KModifier.ABSTRACT)
            .addParameter("screen", screenClass)
            .addParameter("navigator", NAVIGATOR)
            .returns(ClassName("", className))
            .build()
        )
      }

    val constructorBuilder =
      FunSpec.constructorBuilder().apply {
        if (addAssistedInjection) {
          addAnnotation(assistedInject)
        }
        addParameter(
          ParameterSpec.builder("screen", screenClass)
            .apply {
              if (assistedInjection.screen) {
                addAnnotation(assisted)
              }
            }
            .build()
        )
        addParameter(
          ParameterSpec.builder("navigator", NAVIGATOR)
            .apply {
              if (assistedInjection.navigator) {
                addAnnotation(assisted)
              }
            }
            .build()
        )
      }

    val presenterClass =
      TypeSpec.classBuilder(className).apply {
        if (noUi) {
          addKdoc(
            """
         TODO (remove): This Circuit [Presenter] was generated without UI or [CircuitInject], and can be
         used with legacy views via `by circuitState()` in your Fragment or Activity.
          """
              .trimIndent()
          )
        }
        primaryConstructor(constructorBuilder.build())
          .addSuperinterface(PRESENTER.parameterizedBy(screenClass.nestedClass("State")))
          .addProperty(
            PropertySpec.builder("screen", screenClass)
              .addModifiers(KModifier.PRIVATE)
              .initializer("screen")
              .build()
          )
          .addProperty(
            PropertySpec.builder("navigator", NAVIGATOR)
              .addModifiers(KModifier.PRIVATE)
              .initializer("navigator")
              .build()
          )
          .addFunction(
            FunSpec.builder("present")
              .addModifiers(KModifier.OVERRIDE)
              .addAnnotation(COMPOSABLE)
              .returns(screenClass.nestedClass("State"))
              .addStatement("TODO(%S)", "Implement me!")
              .build()
          )
          .addType(factoryBuilder.build())
      }
    return FileSpec.builder(packageName, className).addType(presenterClass.build()).build()
  }
}

class CircuitUiFeature(private val injectClasses: MutableSet<ClassName>) : CircuitComponent {
  override val fileSuffix = ""

  override fun generate(packageName: String, className: String): FileSpec {
    /**
     * Generate UI class, e.g
     *
     * @CircuitInject( ${NAME}Screen::class, UserScope::class, )
     * @Composable fun ${NAME}(state: FeatureScreen.State, modifier: Modifier = Modifier) { }
     */
    val screenClass = ClassName(packageName, screenClassName(className))
    val uiFunction =
      FunSpec.builder(className)
        .addAnnotation(
          AnnotationSpec.builder(CIRCUIT_INJECT)
            .apply {
              addMember("%T::class", screenClass)
              injectClasses.forEach { addMember("%T::class", it) }
            }
            .build()
        )
        .addAnnotation(Composable::class)
        .addParameter(ParameterSpec.builder("state", screenClass.nestedClass("State")).build())
        .addParameter(ParameterSpec.builder("modifier", MODIFIER).defaultValue("Modifier").build())
        .build()

    return FileSpec.builder(packageName, className).addFunction(uiFunction).build()
  }
}

class CircuitTest(override val fileSuffix: String, private val baseClass: String?) :
  CircuitComponent {

  override fun isTestComponent(): Boolean = true

  override fun generate(packageName: String, className: String): FileSpec {
    /** Generate test class, e.g: class FeaturePresenterTest : BaseClass() */
    val testClassSpec =
      TypeSpec.classBuilder("${className}${fileSuffix}")
        .apply {
          if (baseClass != null) {
            superclass(ClassName.bestGuess(baseClass))
          }
        }
        .build()

    return FileSpec.get(packageName, testClassSpec)
  }
}

class CircuitViewModelScreen : CircuitComponent {
  override val fileSuffix = "Screen"

  override fun generate(packageName: String, className: String): FileSpec {
    /**
     * Generate ViewModelScreen class
     *
     * @Parcelize class ${NAME}Screen : Screen {
     *
     *   data class State( val events: Events ) : CircuitUiState
     *
     *     @Immutable interface Events { } }
     */
    val screenClassName = ClassName(packageName, screenClassName(className))
    val eventInterfaceCN = screenClassName.nestedClass("Event")
    val eventInterface =
      TypeSpec.interfaceBuilder(eventInterfaceCN.simpleName).addAnnotation(IMMUTABLE).build()

    val stateClass =
      TypeSpec.classBuilder("State")
        .addModifiers(KModifier.DATA)
        .primaryConstructor(
          FunSpec.constructorBuilder()
            .addParameter(ParameterSpec.builder("events", eventInterfaceCN).build())
            .build()
        )
        .addProperty(PropertySpec.builder("events", eventInterfaceCN).initializer("events").build())
        .addSuperinterface(CIRCUIT_UI_STATE)
        .build()
    val typeSpec =
      TypeSpec.classBuilder(screenClassName)
        .addSuperinterface(SCREEN_INTERFACE)
        .addAnnotation(PARCELIZE)
        .addType(stateClass)
        .addType(eventInterface)
        .build()
    return FileSpec.get(packageName, typeSpec)
  }
}

class CircuitViewModel(private val additionalScope: Set<ClassName> = setOf()) : CircuitComponent {

  override val fileSuffix = "ViewModel"

  override fun generate(packageName: String, className: String): FileSpec {
    /**
     * Generate ViewModel class, e.g:
     *
     * @ContributesMultibinding( UserScope::class, boundType = ViewModel::class, )
     * @ViewModelKey(${NAME}ViewModel::class) class ${NAME}ViewModel @Inject constructor(
     *   slackDispatchers: SlackDispatchers, ) :
     *   UdfViewModel<${NAME}Screen.State>(CloseableCoroutineScope.newMainScope(slackDispatchers)),
     *   ${NAME}Screen.Events { private val state: MutableStateFlow<${NAME}Screen.State> =
     *   MutableStateFlow(${NAME}Screen.State(events = this))
     *
     *   override fun state(): StateFlow<${NAME}Screen.State> = state }
     */
    val viewModel = ClassName(packageName, className)
    val screenClass = ClassName(packageName, screenClassName(className))
    val closeableCoroutineScope =
      ClassName("slack.foundation.coroutines", "CloseableCoroutineScope")

    val stateFun =
      FunSpec.builder("state")
        .addModifiers(KModifier.OVERRIDE)
        .returns(STATE_FLOW.parameterizedBy(screenClass.nestedClass("State")))
        .addStatement("return state")
        .build()

    val viewModelClass =
      TypeSpec.classBuilder(viewModel)
        .addAnnotation(
          AnnotationSpec.builder(
              ClassName("com.squareup.anvil.annotations", "ContributesMultibinding")
            )
            .apply { additionalScope.forEach { scope -> addMember("%T::class", scope) } }
            .addMember("boundType = %T::class", VIEW_MODEL)
            .build()
        )
        .addAnnotation(
          AnnotationSpec.builder(VIEW_MODEL_KEY).addMember("%T::class", viewModel).build()
        )
        .primaryConstructor(
          FunSpec.constructorBuilder()
            .addParameter(ParameterSpec.builder("slackDispatchers", SLACK_DISPATCHER).build())
            .addAnnotation(JAVA_INJECT)
            .build()
        )
        .superclass(UDF_VIEW_MODEL.parameterizedBy(screenClass.nestedClass("State")))
        .addSuperclassConstructorParameter(
          "CloseableCoroutineScope.newMainScope(slackDispatchers)",
          MemberName("slack.foundation.coroutines", "CloseableCoroutineScope.newMainScope"),
        )
        .addSuperinterface(screenClass.nestedClass("Events"))
        .addProperty(
          PropertySpec.builder(
              "state",
              MUTABLE_STATE_FLOW.parameterizedBy(screenClass.nestedClass("State")),
            )
            .initializer("MutableStateFlow(%T(events = this))", screenClass.nestedClass("State"))
            .addModifiers(KModifier.PRIVATE)
            .build()
        )
        .addFunction(stateFun)
        .build()
    return FileSpec.builder(packageName, className)
      .addImport(closeableCoroutineScope.packageName, closeableCoroutineScope.simpleName)
      .addType(viewModelClass)
      .build()
  }
}

data class AssistedInjectionConfig(val screen: Boolean = false, val navigator: Boolean = false)
