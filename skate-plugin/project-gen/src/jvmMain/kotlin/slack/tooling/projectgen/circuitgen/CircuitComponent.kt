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
  import com.squareup.kotlinpoet.ParameterSpec
  import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
  import com.squareup.kotlinpoet.PropertySpec
  import com.squareup.kotlinpoet.TypeSpec
  import com.squareup.kotlinpoet.asTypeName
  import java.io.File
  import java.nio.file.Files
  import java.nio.file.Path
  import kotlin.io.path.name
  import kotlin.io.path.pathString
  import kotlin.io.path.relativeTo
  import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.CIRCUIT_INJECT
  import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.CIRCUIT_UI_EVENT
  import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.CIRCUIT_UI_STATE
  import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.COMPOSABLE
  import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.IMMUTABLE
  import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.JAVA_INJECT
  import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.MODIFIER
  import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.NAVIGATOR
  import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.PARCELIZE
  import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.PRESENTER
  import slack.tooling.projectgen.circuitgen.CircuitGenClassNames.SCREEN_INTERFACE

  interface CircuitComponent {
    val fileSuffix: String

    fun screenClassName(featureName: String): String = "${featureName.replace(fileSuffix, "")}Screen"

    fun generate(packageName: String, className: String): FileSpec

    fun isTestComponent(): Boolean = false

    fun writeToFile(selectedDir: Path, className: String) {
      val dir =
        if (isTestComponent()) Path.of(selectedDir.pathString.replace("src/main", "src/test"))
        else selectedDir
      val srcDir =
        generateSequence(dir) { it.parent }
          .takeWhile { it.name != "kotlin" && it.name != "java" }
          .lastOrNull() ?: dir
      val packageName = dir.relativeTo(srcDir).toString().replace(File.separatorChar, '.')
      val fileSpec = generate(packageName, "$className$fileSuffix")
      fileSpec.writeTo(srcDir)
      removePublicModifier(dir, className)
    }

    fun removePublicModifier(dir: Path, className: String) {
      val generateFilePath = dir.resolve("$className$fileSuffix.kt")
      if (Files.exists(generateFilePath)) {
        val generatedCode = Files.readString(generateFilePath)
        val updatedCode = generatedCode.replace("public ", "")
        Files.writeString(generateFilePath, updatedCode)
      }
    }

    fun createCircuitInjectAnnotation(screenClass: ClassName, injectScope: ClassName): AnnotationSpec {
      return AnnotationSpec.builder(CIRCUIT_INJECT)
        .addMember("%T::class", screenClass)
        .addMember("%T::class", injectScope)
        .build()
    }
  }

  class CircuitScreen : CircuitComponent {
    override val fileSuffix = "Screen"

    override fun generate(packageName: String, className: String): FileSpec {
      /**
       * Generate Circuit Screen class, eg:
       * ```kotlin
       * @Parcelize class ${NAME}Screen : Screen { data class State( val eventSink: Event.() -> Unit =
       *   {}, ) : CircuitUiState
       * @Immutable sealed interface Event : CircuitUiEvent }
       * ```
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
    private val circuitInjectScope: ClassName?,
    private val ui: Boolean = true,
  ) : CircuitComponent {
    override val fileSuffix = "Presenter"

    override fun generate(packageName: String, className: String): FileSpec {
      /**
       * Generate Circuit Presenter class, eg:
       * ```kotlin
       * class ${NAME}Presenter @AssistedInject constructor(
       *   @Assisted private val screen: ${NAME}Screen,
       *   @Assisted private val navigator: Navigator,
       * ) : Presenter<${NAMEScreen.State> {
       *    @Composable
       *    override fun present(): FeatureScreen.State {}
       *
       *    @AssistedFactory
       *    @CircuitInject( ${NAME}Screen::class, UserScope::class, )
       *    fun interface Factory {
       *      fun create(screen: ${NAME}Screen, navigator: Navigator): ${NAME}Presenter
       *    }
       * }
       * ```
       * Without assisted injection, the code generated will be:
       *
       * ```kotlin
       * @CircuitInject( ${NAME}Screen::class, UserScope::class, )
       * class ${NAME}Presenter @Inject constructor(
       *   private val screen: ${NAME}Screen,
       *  ) : Presenter<${NAMEScreen.State> {
       *    @Composable override fun present(): FeatureScreen.State {}
       *  }
       *   ```
       */
      val screenClass = ClassName(packageName, screenClassName(className))
      val assistedInject = ClassName("dagger.assisted", "AssistedInject")
      val assisted = ClassName("dagger.assisted", "Assisted")
      val addAssistedInjection = assistedInjection.screen || assistedInjection.navigator

      val constructorBuilder =
        if (!addAssistedInjection) {
          FunSpec.constructorBuilder()
            .addParameter(
              ParameterSpec.builder("screen", screenClass).build()
            )
            .addAnnotation(JAVA_INJECT)
        } else {
          FunSpec.constructorBuilder().apply {
            addAnnotation(assistedInject)
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
        }

      val presenterClass =
        TypeSpec.classBuilder(className).apply {
          if (!ui) {
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
            .addFunction(
              FunSpec.builder("present")
                .addModifiers(KModifier.OVERRIDE)
                .addAnnotation(COMPOSABLE)
                .returns(screenClass.nestedClass("State"))
                .addStatement("TODO(%S)", "Implement me!")
                .build()
            )
            .apply {
              if (addAssistedInjection) {
                addProperty(
                  PropertySpec.builder("navigator", NAVIGATOR)
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("navigator")
                    .build()
                )
                circuitInjectScope?.let { addType( createFactoryBuilder(screenClass, it, className).build()) }
              } else {
                circuitInjectScope?.let {
                  addAnnotation(
                    createCircuitInjectAnnotation(screenClass, circuitInjectScope)
                  )
                }
              }
            }
        }
      return FileSpec.builder(packageName, className).addType(presenterClass.build()).build()
    }


    private fun createFactoryBuilder(
      screenClass: ClassName,
      injectScope: ClassName,
      className: String,
    ): TypeSpec.Builder {
      val assistedFactory = ClassName("dagger.assisted", "AssistedFactory")
      return TypeSpec.funInterfaceBuilder("Factory")
        .addAnnotation(assistedFactory)
        .addAnnotation(
          createCircuitInjectAnnotation(screenClass, injectScope)
        )
        .addFunction(
          FunSpec.builder("create")
            .addModifiers(KModifier.ABSTRACT)
            .addParameter("screen", screenClass)
            .addParameter("navigator", NAVIGATOR)
            .returns(ClassName("", className))
            .build()
        )
    }
  }

  class CircuitUiFeature(private val circuitInjectScope: ClassName?) : CircuitComponent {
    override val fileSuffix = ""

    override fun generate(packageName: String, className: String): FileSpec {
      /**
       * Generate UI class, e.g
       *
       * ```kotlin
       *
       * @CircuitInject(${NAME}Screen::class, UserScope::class,)
       * @Composable fun ${NAME}(state: FeatureScreen.State, modifier: Modifier = Modifier) {
       * }
       * ```
       */
      val screenClass = ClassName(packageName, screenClassName(className))
      val uiFunction =
        FunSpec.builder(className)
          .apply {
            if (circuitInjectScope != null) {
              addAnnotation(
                createCircuitInjectAnnotation(screenClass, circuitInjectScope)
              )
            }
          }
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
      /**
       * Generate test class, e.g:
       * ```kotlin
       * class FeaturePresenterTest : BaseClass()
       * ```
       */
      val testClassSpec =
        TypeSpec.classBuilder(className)
          .apply {
            if (baseClass != null) {
              superclass(ClassName.bestGuess(baseClass))
            }
          }
          .build()

      return FileSpec.get(packageName, testClassSpec)
    }
  }

  data class AssistedInjectionConfig(val screen: Boolean = false, val navigator: Boolean = false)
