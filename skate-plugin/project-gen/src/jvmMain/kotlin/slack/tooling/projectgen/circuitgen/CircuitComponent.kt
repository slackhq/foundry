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

  fun generate(packageName: String, className: String): FileSpec

  fun isTestComponent(): Boolean = false

  fun writeToFile(directory: String, packageName: String?, className: String) {
    val baseDirectory =
      if (isTestComponent()) directory.replace("src/main", "src/test") else directory
    val fileSpec = generate(packageName.orEmpty(), className)
    File(baseDirectory).apply { if (!exists()) mkdirs() }
    val stringWriter = StringWriter()
    fileSpec.writeTo(stringWriter)
    val generatedCode = stringWriter.toString().replace("public ", "")
    File("${baseDirectory}/${className}${fileSuffix}.kt").writeText(generatedCode)
  }
}

class CircuitScreen : CircuitComponent {
  override val fileSuffix = "Screen"

  override fun generate(packageName: String, className: String): FileSpec {
    /*
    Generate Circuit Screen class, eg:

    @Parcelize
      class FeatureScreen : Screen {
        data class State(
          val message: String = "",
          val eventSink: Event.() -> Unit = {},
        ) : CircuitUiState

        @Immutable
        sealed interface Event : CircuitUiEvent
      }
    */
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
    val stateClass =
      TypeSpec.classBuilder("State")
        .addModifiers(KModifier.DATA)
        .primaryConstructor(
          FunSpec.constructorBuilder()
            .addParameter(
              ParameterSpec.builder("message", String::class).defaultValue("%S", "").build()
            )
            .addParameter(
              ParameterSpec.builder(
                  "eventSink",
                  LambdaTypeName.get(
                    receiver = eventInterfaceName,
                    returnType = Unit::class.asTypeName(),
                  ),
                )
                .defaultValue("{}")
                .build()
            )
            .build()
        )
        .addProperty(PropertySpec.builder("message", String::class).initializer("message").build())
        .addProperty(
          PropertySpec.builder(
              "eventSink",
              LambdaTypeName.get(
                receiver = eventInterfaceName,
                returnType = Unit::class.asTypeName(),
              ),
            )
            .initializer("eventSink")
            .build()
        )
        .addSuperinterface(circuitUiState)
        .build()

    return FileSpec.builder(packageName, circuitScreenClass)
      .addType(
        TypeSpec.classBuilder(circuitScreenClass)
          .addSuperinterface(screenInterface)
          .addAnnotation(parcelize)
          .addType(stateClass)
          .addType(eventInterface)
          .build()
      )
      .build()
  }
}

class CircuitPresenter(
  private val useAssistedInjection: Boolean = true,
  private val noUi: Boolean = false,
) : CircuitComponent {
  override val fileSuffix = "Presenter"

  override fun generate(packageName: String, className: String): FileSpec {
    /*
     Generate Circuit Presenter class, eg:

     class FeaturePresenter @AssistedInject constructor(
       @Assisted
       private val screen: {Feature}Screen,
       @Assisted
       private val navigator: Navigator,
     ) : Presenter<ggScreen.State> {
       @Composable
       override fun present(): FeatureScreen.State {
         val scope = rememberStableCoroutineScope()
         return FeatureScreen.State() { event ->
         }
       }

       @AssistedFactory
       @CircuitInject(
         FeatureScreen::class,
         UserScope::class,
       )
       interface Factory {
         fun create(screen: ggScreen, navigator: Navigator): FeaturePresenter {
         }
       }
     }
    */
    val screenClassName = "${className}Screen"
    val presenterClassName = "${className}Presenter"
    val assistedInject = ClassName("dagger.assisted", "AssistedInject")
    val assisted = ClassName("dagger.assisted", "Assisted")
    val assistedFactory = ClassName("dagger.assisted", "AssistedFactory")

    val factoryBuilder =
      TypeSpec.interfaceBuilder("Factory").apply {
        if (useAssistedInjection) {
          addAnnotation(assistedFactory)
        }
      }

    val constructorBuilder =
      FunSpec.constructorBuilder().apply {
        if (useAssistedInjection) {
          addAnnotation(assistedInject)
        }
        addParameter(
          ParameterSpec.builder("screen", ClassName("", screenClassName))
            .apply {
              if (useAssistedInjection) {
                addAnnotation(assisted)
              }
            }
            .build()
        )
        addParameter(
          ParameterSpec.builder("navigator", ClassName("com.slack.circuit.runtime", "Navigator"))
            .apply {
              if (useAssistedInjection) {
                addAnnotation(assisted)
              }
            }
            .build()
        )
      }

    val presenterClass =
      TypeSpec.classBuilder(presenterClassName).apply {
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
          .addSuperinterface(
            ClassName("com.slack.circuit.runtime.presenter", "Presenter")
              .parameterizedBy(ClassName(packageName, "${className}Screen.State"))
          )
          .addProperty(
            PropertySpec.builder("screen", ClassName("", "${className}Screen"))
              .addModifiers(KModifier.PRIVATE)
              .initializer("screen")
              .build()
          )
          .addProperty(
            PropertySpec.builder("navigator", ClassName("com.slack.circuit.runtime", "Navigator"))
              .addModifiers(KModifier.PRIVATE)
              .initializer("navigator")
              .build()
          )
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
            """
                  .trimIndent()
              )
              .build()
          )
          .addType(
            factoryBuilder
              .addAnnotation(
                AnnotationSpec.builder(
                    ClassName("com.slack.circuit.codegen.annotations", "CircuitInject")
                  )
                  .addMember("%T::class", ClassName(packageName, "${className}Screen"))
                  .addMember("%T::class", ClassName("slack.di", "UserScope"))
                  .build()
              )
              .addFunction(
                FunSpec.builder("create")
                  .addParameter("screen", ClassName(packageName, "${className}Screen"))
                  .addParameter("navigator", ClassName("com.slack.circuit.runtime", "Navigator"))
                  .returns(ClassName("", "${className}Presenter"))
                  .build()
              )
              .build()
          )
      }

    return FileSpec.builder(packageName, presenterClassName).addType(presenterClass.build()).build()
  }
}

class CircuitUiFeature : CircuitComponent {
  override val fileSuffix = ""

  override fun generate(packageName: String, className: String): FileSpec {
    /*
    @CircuitInject(
      FeatureScreen::class,
      UserScope::class,
    )
    @Composable
    fun Feature(state: ggScreen.State, modifier: Modifier = Modifier) {
    }
     */
    val uiFunction =
      FunSpec.builder(className)
        .addAnnotation(
          AnnotationSpec.builder(
              ClassName("com.slack.circuit.codegen.annotations", "CircuitInject")
            )
            .addMember("%T::class", ClassName(packageName, "${className}Screen"))
            .addMember("%T::class", ClassName("slack.di", "UserScope"))
            .build()
        )
        .addAnnotation(Composable::class)
        .addParameter(
          ParameterSpec.builder("state", ClassName(packageName, "${className}Screen.State")).build()
        )
        .addParameter(
          ParameterSpec.builder("modifier", ClassName("androidx.compose.ui", "Modifier"))
            .defaultValue("Modifier")
            .build()
        )
        .build()

    return FileSpec.builder(packageName, className).addFunction(uiFunction).build()
  }
}

class CircuitTest(override val fileSuffix: String, private val baseClass: String?) :
  CircuitComponent {

  override fun isTestComponent(): Boolean = true

  override fun generate(packageName: String, className: String): FileSpec {
    /*
    class FeaturePresenterTest : BaseClass()
    */
    val testClassSpec =
      TypeSpec.classBuilder("${className}${fileSuffix}")
        .apply {
          if (baseClass != null) {
            superclass(ClassName.bestGuess(baseClass))
          }
        }
        .build()

    return FileSpec.builder(packageName, "${className}${fileSuffix}").addType(testClassSpec).build()
  }
}
