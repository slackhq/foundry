/*
 * Copyright (C) 2022 Slack Technologies, LLC
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
@file:Suppress("UnnecessaryAbstractClass")

package foundry.gradle

import app.cash.sqldelight.gradle.SqlDelightExtension
import app.cash.sqldelight.gradle.SqlDelightTask
import com.android.build.api.AndroidPluginVersion
import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.internal.tasks.databinding.DataBindingGenBaseClassesTask
import com.google.devtools.ksp.gradle.KspExtension
import com.squareup.anvil.plugin.AnvilExtension
import dev.zacsweers.moshix.ir.gradle.MoshiPluginExtension
import foundry.gradle.agp.PermissionAllowlistConfigurer
import foundry.gradle.anvil.AnvilMode
import foundry.gradle.compose.COMPOSE_COMPILER_OPTION_PREFIX
import foundry.gradle.dependencies.FoundryDependencies
import foundry.gradle.util.addKspSource
import foundry.gradle.util.configureKotlinCompilationTask
import foundry.gradle.util.setDisallowChanges
import java.io.File
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag
import org.jetbrains.kotlin.gradle.utils.named

@DslMarker public annotation class FoundryExtensionMarker

@FoundryExtensionMarker
public abstract class FoundryExtension
@Inject
constructor(
  objects: ObjectFactory,
  globalFoundryProperties: FoundryProperties,
  private val foundryProperties: FoundryProperties,
  project: Project,
  versionCatalog: VersionCatalog,
) {
  internal val androidHandler = objects.newInstance<AndroidHandler>(foundryProperties)
  internal val featuresHandler =
    objects.newInstance<FeaturesHandler>(
      globalFoundryProperties,
      foundryProperties,
      project,
      versionCatalog,
    )

  /**
   * This is weird! Due to the non-property nature of some AGP DSL features (e.g. buildFeatures and
   * composeOptions DSLs), we can't lazily chain their values to our own extension's properties.
   * Because of this, we lazily set this instance from [StandardProjectConfigurations] during
   * Android extension evaluation and then make calls to enable them _directly_ set the values on
   * this instance. Ideally we could eventually remove this if/when AGP finally makes these
   * properties lazy.
   */
  private var androidExtension: CommonExtension<*, *, *, *, *, *>? = null
    set(value) {
      field = value
      androidHandler.setAndroidExtension(value)
      featuresHandler.setAndroidExtension(value)
    }

  internal fun setAndroidExtension(androidExtension: CommonExtension<*, *, *, *, *, *>) {
    this.androidExtension = androidExtension
  }

  public fun android(action: Action<AndroidHandler>) {
    action.execute(androidHandler)
  }

  public fun features(action: Action<FeaturesHandler>) {
    action.execute(featuresHandler)
  }

  internal fun applyTo(project: Project) {
    val logVerbose = foundryProperties.foundryExtensionVerbose
    // Dirty but necessary since the extension isn't configured yet when we call this
    project.afterEvaluate {
      featuresHandler.applyTo(project)

      var kaptRequired = false
      val moshiCodegenEnabled = featuresHandler.moshiHandler.moshiCodegen.getOrElse(false)
      val moshiSealedCodegenEnabled = featuresHandler.moshiHandler.sealedCodegen.getOrElse(false)
      val allowKsp = foundryProperties.enableKsp
      val allowMoshiIr = foundryProperties.enableMoshiIr
      val anvilMode = foundryProperties.anvilMode
      val allowDaggerKsp = anvilMode.useKspContributionMerging && anvilMode.useDaggerKsp

      /** Marks this project as needing kapt code gen. */
      fun markKaptNeeded(source: String) {
        kaptRequired = true
        // Apply kapt for them
        pluginManager.apply("org.jetbrains.kotlin.kapt")
        if (logVerbose) {
          logger.lifecycle(
            """
            [kapt Config]
            project = $path
            source = $source
            """
              .trimIndent()
          )
        }
      }

      /** Marks this project as needing KSP code gen. */
      fun markKspNeeded(source: String) {
        if (logVerbose) {
          logger.lifecycle(
            """
            [KSP Config]
            project = $path
            KSP source = $source
            """
              .trimIndent()
          )
        }
        if (!isUsingKsp) {
          // Apply KSP for them
          pluginManager.apply("com.google.devtools.ksp")
        }
      }

      /** Marks this project as needing the Moshi Gradle Plugin. */
      fun markMoshiGradleNeeded(source: String, enableSealed: Boolean) {
        if (logVerbose) {
          logger.lifecycle(
            """
            [Moshi Gradle Config]
            project = $path
            source = $source
            """
              .trimIndent()
          )
        }
        if (!isUsingMoshiGradle) {
          // Apply Moshi gradle for them
          pluginManager.apply("dev.zacsweers.moshix")
        }
        if (enableSealed) {
          configure<MoshiPluginExtension> { this.enableSealed.setDisallowChanges(true) }
        }
        if (foundryProperties.moshixGenerateProguardRules) {
          markKspNeeded("Moshi IR code gen")
        }
      }

      fun aptConfiguration(variant: String = ""): String {
        return if (isKotlin) {
          "kapt${variant.capitalizeUS()}"
        } else {
          "annotationProcessor${variant.capitalizeUS()}"
        }
      }

      fun kspConfiguration(variant: String = ""): String {
        return "ksp${variant.capitalizeUS()}"
      }

      // Dagger is configured first. If Dagger's compilers are present,
      // everything else needs to also use kapt!
      val daggerConfig =
        featuresHandler.daggerHandler.computeConfig(
          featuresHandler.testFixturesUseDagger.getOrElse(false)
        )
      val useAnyKspAnvilMode = anvilMode.useKspFactoryGen || anvilMode.useKspContributionMerging
      if (daggerConfig != null) {
        dependencies.add("implementation", FoundryDependencies.Dagger.dagger)
        dependencies.add("implementation", FoundryDependencies.javaxInject)

        val anvilPackage =
          if (foundryProperties.anvilUseKspFork) {
            "dev.zacsweers.anvil"
          } else {
            "com.squareup.anvil"
          }
        if (daggerConfig.runtimeOnly) {
          val annotations = "$anvilPackage:annotations"
          dependencies.add("compileOnly", annotations)
          if (daggerConfig.testFixturesUseDagger) {
            dependencies.add("testFixturesCompileOnly", annotations)
          }
        }

        if (logVerbose) {
          logger.lifecycle(
            """
            [Dagger Config]
            project = $path
            daggerConfig = $daggerConfig
            """
              .trimIndent()
          )
        }

        if (daggerConfig.enableAnvil) {
          if (!foundryProperties.disableAnvilForK2Testing) {
            val anvilId =
              if (foundryProperties.anvilUseKspFork) {
                "dev.zacsweers.anvil"
              } else {
                "com.squareup.anvil"
              }
            pluginManager.apply(anvilId)
            val anvilExtension = extensions.getByType<AnvilExtension>()
            anvilExtension.apply {
              generateDaggerFactories.setDisallowChanges(daggerConfig.anvilFactories)
              generateDaggerFactoriesOnly.setDisallowChanges(daggerConfig.anvilFactoriesOnly)
            }

            if (useAnyKspAnvilMode) {
              // Workaround early application for https://github.com/google/ksp/issues/1789
              pluginManager.apply("com.google.devtools.ksp")
              anvilExtension.useKsp(
                contributesAndFactoryGeneration = true,
                componentMerging = anvilMode.useKspContributionMerging,
              )

              if (daggerConfig.testFixturesUseDagger) {
                dependencies.add(kspConfiguration("testFixtures"), "$anvilPackage:compiler")
              }

              // Make KSP depend on sqldelight and viewbinding tasks
              // This is opt-in as it's better for build performance to skip this linking if
              // possible
              // TODO KSP is supposed to do this automatically in android projects per
              //  https://github.com/google/ksp/pull/1739, but that doesn't seem to actually work
              //  let's make this optional
              // afterEvaluate is necessary in order to wait for tasks to exist
              if (
                foundryProperties.kspConnectSqlDelight || foundryProperties.kspConnectViewBinding
              ) {
                afterEvaluate {
                  if (
                    foundryProperties.kspConnectSqlDelight &&
                      pluginManager.hasPlugin("app.cash.sqldelight")
                  ) {
                    val dbNames = extensions.getByType<SqlDelightExtension>().databases.names
                    val sourceSet =
                      when {
                        isKotlinMultiplatform -> "CommonMain"
                        isAndroidLibrary -> "Release"
                        else -> "Main"
                      }
                    val sourceSetKspName =
                      when {
                        isKotlinMultiplatform -> "CommonMainMetadata"
                        isAndroidLibrary -> "Release"
                        else -> ""
                      }
                    for (dbName in dbNames) {
                      val sqlDelightTask =
                        tasks.named<SqlDelightTask>("generate${sourceSet}${dbName}Interface")
                      val outputProvider = sqlDelightTask.flatMap { it.outputDirectory }
                      project.addKspSource(
                        "ksp${sourceSetKspName}Kotlin",
                        sqlDelightTask,
                        outputProvider,
                      )
                    }
                  }

                  // If using viewbinding, need to wire those up too
                  if (
                    foundryProperties.kspConnectViewBinding &&
                      isAndroidLibrary &&
                      !foundryProperties.libraryWithVariants &&
                      androidHandler.isViewBindingEnabled
                  ) {
                    val databindingTask =
                      tasks.named<DataBindingGenBaseClassesTask>("dataBindingGenBaseClassesRelease")
                    val databindingOutputProvider = databindingTask.flatMap { it.sourceOutFolder }
                    project.addKspSource(
                      "kspReleaseKotlin",
                      databindingTask,
                      databindingOutputProvider,
                    )
                  }
                }
              }
            }

            if (anvilMode == AnvilMode.K1_EMBEDDED) {
              val generatorProjects =
                buildSet<Any> {
                  addAll(
                    foundryProperties.anvilGeneratorProjects
                      ?.splitToSequence(";")
                      ?.map(::project)
                      .orEmpty()
                  )
                  addAll(featuresHandler.daggerHandler.anvilGenerators)
                }
              for (generator in generatorProjects) {
                dependencies.add("anvil", generator)
              }
            }
          }

          val runtimeProjects =
            foundryProperties.anvilRuntimeProjects?.splitToSequence(";")?.toSet().orEmpty()

          for (runtimeProject in runtimeProjects) {
            dependencies.add("implementation", project(runtimeProject))
            if (daggerConfig.testFixturesUseDagger) {
              dependencies.add("testFixturesImplementation", project(runtimeProject))
            }
          }
        }

        if (!daggerConfig.runtimeOnly && daggerConfig.useDaggerCompiler) {
          if (allowDaggerKsp && (!daggerConfig.enableAnvil || anvilMode.useDaggerKsp)) {
            markKspNeeded("Dagger compiler")
            dependencies.add(kspConfiguration(""), FoundryDependencies.Dagger.compiler)
            // Currently we don't support dagger-compiler or components in test fixtures, but if we
            // did it would go here
          } else {
            markKaptNeeded("Dagger compiler")
            dependencies.add(aptConfiguration(), FoundryDependencies.Dagger.compiler)
          }
        }
      }

      if (featuresHandler.circuitHandler.codegen.getOrElse(false)) {
        markKspNeeded("Circuit")
        dependencies.add("ksp", "com.slack.circuit:circuit-codegen")
        dependencies.add("compileOnly", "com.slack.circuit:circuit-codegen-annotations")
      }

      if (featuresHandler.autoService.getOrElse(false)) {
        if (allowKsp) {
          markKspNeeded("AutoService")
          dependencies.add("implementation", FoundryDependencies.Auto.Service.annotations)
          dependencies.add("ksp", FoundryDependencies.Auto.Service.ksp)
        } else {
          markKaptNeeded("AutoService")
          dependencies.add("compileOnly", FoundryDependencies.Auto.Service.annotations)
          dependencies.add(aptConfiguration(), FoundryDependencies.Auto.Service.autoservice)
        }
      }

      if (featuresHandler.redacted.getOrElse(false)) {
        pluginManager.apply("dev.zacsweers.redacted")
      }

      if (featuresHandler.moshiHandler.moshi.getOrElse(false)) {
        dependencies.add("implementation", FoundryDependencies.Moshi.moshi)
        if (moshiCodegenEnabled) {
          if (allowMoshiIr) {
            markMoshiGradleNeeded("Moshi code gen", false)
          } else if (allowKsp) {
            markKspNeeded("Moshi code gen")
            dependencies.add("ksp", FoundryDependencies.Moshi.codeGen)
          } else {
            markKaptNeeded("Moshi code gen")
            dependencies.add(aptConfiguration(), FoundryDependencies.Moshi.codeGen)
          }
        }
        if (featuresHandler.moshiHandler.moshiAdapters.getOrElse(false)) {
          dependencies.add("implementation", FoundryDependencies.Moshi.adapters)
        }
        if (featuresHandler.moshiHandler.moshiKotlinReflect.getOrElse(false)) {
          dependencies.add("implementation", FoundryDependencies.Moshi.kotlinReflect)
        }
        if (featuresHandler.moshiHandler.moshixAdapters.getOrElse(false)) {
          dependencies.add("implementation", FoundryDependencies.Moshi.MoshiX.adapters)
        }
        if (featuresHandler.moshiHandler.moshixMetadataReflect.getOrElse(false)) {
          dependencies.add("implementation", FoundryDependencies.Moshi.MoshiX.metadataReflect)
        }
        if (featuresHandler.moshiHandler.lazyAdapters.getOrElse(false)) {
          dependencies.add("implementation", FoundryDependencies.Moshi.lazyAdapters)
        }
        if (featuresHandler.moshiHandler.sealed.getOrElse(false)) {
          dependencies.add("implementation", FoundryDependencies.Moshi.MoshiX.Sealed.runtime)
          if (moshiSealedCodegenEnabled) {
            if (allowMoshiIr) {
              markMoshiGradleNeeded("Moshi sealed codegen", enableSealed = true)
            } else if (allowKsp) {
              markKspNeeded("Moshi sealed codegen")
              dependencies.add("ksp", FoundryDependencies.Moshi.MoshiX.Sealed.codegen)
            } else {
              markKaptNeeded("Moshi sealed codegen")
              dependencies.add(aptConfiguration(), FoundryDependencies.Moshi.MoshiX.Sealed.codegen)
            }
          }
          if (featuresHandler.moshiHandler.sealedReflect.getOrElse(false)) {
            dependencies.add("implementation", FoundryDependencies.Moshi.MoshiX.Sealed.reflect)
          }
          if (featuresHandler.moshiHandler.sealedMetadataReflect.getOrElse(false)) {
            dependencies.add(
              "implementation",
              FoundryDependencies.Moshi.MoshiX.Sealed.metadataReflect,
            )
          }
        }
      }

      // At the very end we check if kapt is enabled and disable anvil component merging if needed
      // https://github.com/square/anvil#incremental-kotlin-compilation-breaks-compiler-plugins
      if (
        kaptRequired &&
          daggerConfig?.enableAnvil == true &&
          !daggerConfig.alwaysEnableAnvilComponentMerging
      ) {
        configure<AnvilExtension> { disableComponentMerging.setDisallowChanges(true) }
        if (useAnyKspAnvilMode) {
          configure<KspExtension> { arg("disable-component-merging", "true") }
        }
      }
    }
  }
}

@FoundryExtensionMarker
public abstract class FeaturesHandler
@Inject
constructor(
  objects: ObjectFactory,
  globalFoundryProperties: FoundryProperties,
  private val foundryProperties: FoundryProperties,
  private val project: Project,
  versionCatalog: VersionCatalog,
) {
  // Dagger features
  internal val daggerHandler = objects.newInstance<DaggerHandler>()

  // Circuit features
  internal val circuitHandler = objects.newInstance<CircuitHandler>()

  /** Enables AutoService on this project. */
  internal abstract val autoService: Property<Boolean>

  /** Enables redacted-compiler-plugin on this project. */
  internal abstract val redacted: Property<Boolean>

  internal val testFixtures: Property<Boolean> = objects.property<Boolean>().convention(false)
  internal val testFixturesUseDagger: Property<Boolean> =
    objects.property<Boolean>().convention(false)

  // Moshi
  internal val moshiHandler = objects.newInstance<MoshiHandler>()

  // Compose features
  internal val composeHandler =
    objects.newInstance<ComposeHandler>(globalFoundryProperties, foundryProperties, versionCatalog)

  /** @see [FoundryExtension.androidExtension] */
  private var androidExtension: CommonExtension<*, *, *, *, *, *>? = null
    set(value) {
      field = value
      composeHandler.setAndroidExtension(value)
    }

  internal fun setAndroidExtension(androidExtension: CommonExtension<*, *, *, *, *, *>?) {
    this.androidExtension = androidExtension
  }

  /**
   * Configures Circuit on this project.
   *
   * @param codegen configures code gen on this project, including KSP setup and annotations. True
   *   by default.
   * @param runtime configures the circuit-runtime dependencies, including screen/ui/presenter. True
   *   by default.
   * @param commonBundle configures the project-common dependencies, defined by the circuit-common
   *   bundle ID in `libs.versions.toml`. True by default.
   * @param foundation configures the circuit-foundation dependency, false by default.
   * @param action optional configuration block for further Circuit config, such as CircuitX.
   */
  public fun circuit(
    codegen: Boolean = true,
    runtime: Boolean = true,
    commonBundle: Boolean = true,
    foundation: Boolean = false,
    action: Action<CircuitHandler> = Action {},
  ) {
    circuitHandler.codegen.set(codegen)
    circuitHandler.runtime.set(runtime)
    circuitHandler.commonBundle.set(commonBundle)
    circuitHandler.foundation.set(foundation)
    action.execute(circuitHandler)
  }

  /**
   * Enables dagger for this project.
   *
   * @param action optional block for extra configuration, such as anvil generators or android.
   */
  public fun dagger(action: Action<DaggerHandler>? = null) {
    daggerHandler.enabled.setDisallowChanges(true)
    action?.execute(daggerHandler)
  }

  /**
   * Enables dagger for this project.
   *
   * @param enableComponents enables dagger components in this project, which in turn imposes use of
   *   the dagger compiler (slower!)
   * @param projectHasJavaInjections indicates if this project has injected _Java_ files. This means
   *   any Java file with `@Inject` or `@AssistedInject`. This imposes use of the dagger compiler
   *   (slower!) because Anvil only processes Kotlin files.
   * @param action optional block for extra configuration, such as anvil generators or android.
   */
  @DelicateFoundryGradlePluginApi
  public fun dagger(
    enableComponents: Boolean = false,
    projectHasJavaInjections: Boolean = false,
    action: Action<DaggerHandler>? = null,
  ) {
    check(enableComponents || projectHasJavaInjections) {
      "This function should not be called with both enableComponents and projectHasJavaInjections set to false. Either remove these parameters or call a more appropriate non-delicate dagger() overload."
    }
    daggerHandler.enabled.setDisallowChanges(true)
    daggerHandler.useDaggerCompiler.setDisallowChanges(true)
    action?.execute(daggerHandler)
  }

  /** Adds dagger's runtime as dependencies to this but runs no code generation. */
  public fun daggerRuntimeOnly() {
    daggerHandler.enabled.setDisallowChanges(true)
    daggerHandler.runtimeOnly.setDisallowChanges(true)
  }

  /**
   * Enables Moshi for this project.
   *
   * @param codegen Enables codegen.
   * @param adapters Enables moshi-adapters.
   * @param kotlinReflect Enables kotlin-reflect-based support. Should only be used in unit tests or
   *   CLIs!
   * @param action Optional extra configuration for other Moshi libraries, such as MoshiX.
   */
  public fun moshi(
    codegen: Boolean,
    adapters: Boolean = false,
    kotlinReflect: Boolean = false,
    action: Action<MoshiHandler> = Action {},
  ) {
    action.execute(moshiHandler)
    moshiHandler.moshi.setDisallowChanges(true)
    moshiHandler.moshiAdapters.setDisallowChanges(adapters)
    moshiHandler.moshiCodegen.setDisallowChanges(codegen)
    moshiHandler.moshiKotlinReflect.setDisallowChanges(kotlinReflect)
  }

  /** Enables AutoService on this project. */
  public fun autoService() {
    autoService.setDisallowChanges(true)
  }

  /** Enables redacted-compiler-plugin on this project. */
  public fun redacted() {
    redacted.setDisallowChanges(true)
  }

  /** Enables test fixtures on this project. */
  // TODO rename this back to testFixtures() once it doesn't conflict with
  //  gradle-dependencies-sorter
  public fun enableTestFixtures() {
    enableTestFixtures(includeDagger = false)
  }

  /**
   * Enables test fixtures on this project.
   *
   * **NOTE**: This is currently private because it appears that KSP does not yet work on test
   * fixtures. Left as a toe-hold for future use though. See
   * https://github.com/google/ksp/issues/2093.
   *
   * @param includeDagger if enabled, will run dagger/anvil factory generation over test fixtures
   *   sources. This is _only_ necessary if you are contributing classes or Dagger modules from your
   *   test fixtures.
   */
  private fun enableTestFixtures(includeDagger: Boolean = false) {
    testFixtures.setDisallowChanges(true)
    testFixturesUseDagger.setDisallowChanges(includeDagger)
    if (androidExtension != null) {
      (androidExtension as? LibraryExtension)?.let { it.testFixtures.enable = true }
        ?: error("Attempted to enable test fixtures on non-library project ${project.path}")
    } else {
      project.pluginManager.apply("java-test-fixtures")
    }
  }

  /**
   * Enables Compose for this project and applies any version catalog bundle dependencies defined by
   * [FoundryProperties.defaultComposeAndroidBundleAlias].
   */
  public fun compose(multiplatform: Boolean = false, action: Action<ComposeHandler> = Action {}) {
    composeHandler.enable(project = project, multiplatform = multiplatform)
    action.execute(composeHandler)
  }

  internal fun applyTo(project: Project) {
    composeHandler.applyTo(project)
    circuitHandler.applyTo(project, foundryProperties)
    // Validate we've enabled dagger if we requested test fixtures with dagger code
    if (testFixturesUseDagger.getOrElse(false) && !daggerHandler.enabled.getOrElse(false)) {
      error(
        "In order to enable test fixtures with dagger, you must also enable " +
          "the `foundry { features { dagger() } }` feature"
      )
    }
  }
}

@FoundryExtensionMarker
public abstract class MoshiHandler {
  internal abstract val moshi: Property<Boolean>
  internal abstract val moshiAdapters: Property<Boolean>
  internal abstract val moshiCodegen: Property<Boolean>
  internal abstract val moshiKotlinReflect: Property<Boolean>

  internal abstract val moshixAdapters: Property<Boolean>
  internal abstract val moshixMetadataReflect: Property<Boolean>

  internal abstract val sealed: Property<Boolean>
  internal abstract val sealedCodegen: Property<Boolean>
  internal abstract val sealedReflect: Property<Boolean>
  internal abstract val sealedMetadataReflect: Property<Boolean>

  internal abstract val lazyAdapters: Property<Boolean>

  /**
   * Enables MoshiX on this project.
   *
   * @param adapters Enables moshix-adapters.
   * @param metadataReflect Enables metadata-reflect. Should only be used in unit tests or CLIs!
   */
  public fun moshix(adapters: Boolean, metadataReflect: Boolean = false) {
    moshixAdapters.setDisallowChanges(adapters)
    moshixMetadataReflect.setDisallowChanges(metadataReflect)
  }

  /**
   * Enables MoshiX-sealed on this project. This is used for polymorphic types.
   *
   * @param codegen Enables codegen.
   * @param kotlinReflect Enables kotlin-reflect-based support. Should only be used in unit tests or
   *   CLIs!
   * @param metadataReflect Enables metadata-based reflection support. Should only be used in unit
   *   tests or CLIs!
   */
  public fun sealed(
    codegen: Boolean,
    kotlinReflect: Boolean = false,
    metadataReflect: Boolean = false,
  ) {
    sealed.setDisallowChanges(true)
    sealedCodegen.setDisallowChanges(codegen)
    sealedReflect.setDisallowChanges(kotlinReflect)
    sealedMetadataReflect.setDisallowChanges(metadataReflect)
  }

  /** Enables [moshi-lazy-adapters](https://github.com/serj-lotutovici/moshi-lazy-adapters). */
  public fun lazyAdapters() {
    lazyAdapters.setDisallowChanges(true)
  }
}

/** DSL for configuring Circuit. */
@FoundryExtensionMarker
public abstract class CircuitHandler @Inject constructor(objects: ObjectFactory) {
  /** Sets up circuit code gen, includes annotations and KSP setup. */
  public val codegen: Property<Boolean> = objects.property<Boolean>().convention(false)

  /** Includes the circuit-runtime dep (including screen, ui, presenter). */
  public val runtime: Property<Boolean> = objects.property<Boolean>().convention(false)

  /** Includes the circuit-foundation dep. */
  public val foundation: Property<Boolean> = objects.property<Boolean>().convention(false)

  /**
   * When enabled, includes a common bundle as defined by the `circuit-common` bundle ID in
   * `libs.versions.toml`.
   */
  public val commonBundle: Property<Boolean> = objects.property<Boolean>().convention(false)
  public val circuitx: CircuitXHandler = objects.newInstance<CircuitXHandler>()

  /** DSL entrypoint for CircuitX dependencies. */
  public fun circuitx(action: Action<CircuitXHandler>) {
    action.execute(circuitx)
  }

  internal fun applyTo(project: Project, foundryProperties: FoundryProperties) {
    if (runtime.getOrElse(false)) {
      project.dependencies.add("implementation", "com.slack.circuit:circuit-runtime")
      project.dependencies.add("implementation", "com.slack.circuit:circuit-runtime-presenter")
      project.dependencies.add("implementation", "com.slack.circuit:circuit-runtime-ui")
      project.dependencies.add("testImplementation", "com.slack.circuit:circuit-test")
    }
    if (foundation.getOrElse(false)) {
      project.dependencies.add("implementation", "com.slack.circuit:circuit-foundation")
    }
    if (commonBundle.getOrElse(false)) {
      foundryProperties.versions.bundles.commonCircuit.ifPresent {
        project.dependencies.add("implementation", it)
      }
    }

    circuitx.applyTo(project)
  }

  @FoundryExtensionMarker
  public abstract class CircuitXHandler @Inject constructor(objects: ObjectFactory) {
    /** Corresponds to the circuitx-android artifact. */
    public val android: Property<Boolean> = objects.property<Boolean>().convention(false)

    /** Corresponds to the circuitx-gesture-navigation artifact. */
    public val gestureNav: Property<Boolean> = objects.property<Boolean>().convention(false)

    /** Corresponds to the circuitx-overlays artifact. */
    public val overlays: Property<Boolean> = objects.property<Boolean>().convention(false)

    internal fun applyTo(project: Project) {
      if (android.getOrElse(false)) {
        project.dependencies.add("implementation", "com.slack.circuit:circuitx-android")
      }
      if (gestureNav.getOrElse(false)) {
        project.dependencies.add("implementation", "com.slack.circuit:circuitx-gesture-navigation")
      }
      if (overlays.getOrElse(false)) {
        project.dependencies.add("implementation", "com.slack.circuit:circuitx-overlays")
      }
    }
  }
}

@FoundryExtensionMarker
public abstract class DaggerHandler @Inject constructor(objects: ObjectFactory) {
  internal val enabled: Property<Boolean> = objects.property<Boolean>().convention(false)
  internal val useDaggerCompiler: Property<Boolean> = objects.property<Boolean>().convention(false)
  internal val disableAnvil: Property<Boolean> = objects.property<Boolean>().convention(false)
  internal val runtimeOnly: Property<Boolean> = objects.property<Boolean>().convention(false)
  internal val alwaysEnableAnvilComponentMerging: Property<Boolean> =
    objects.property<Boolean>().convention(false)
  internal val anvilGenerators = objects.domainObjectSet<Any>()

  /**
   * Dependencies for Anvil generators that should be added. These should be in the same form as
   * they would be added to regular project dependencies.
   *
   * ```
   * foundry {
   *   features {
   *     dagger(...) {
   *       anvilGenerators(projects.libraries.foundation.anvil.injection.compiler)
   *     }
   *   }
   * }
   * ```
   */
  public fun anvilGenerators(vararg generators: ProjectDependency) {
    anvilGenerators.addAll(generators)
  }

  /**
   * By default, if kapt is enabled we will disable anvil component merging as an optimization as it
   * incurs a cost of disabling incremental kapt stubs. If we need it though (aka this is running in
   * app-di or another project that actually has components), this can be always enabled as needed.
   */
  @DelicateFoundryGradlePluginApi
  public fun alwaysEnableAnvilComponentMerging() {
    alwaysEnableAnvilComponentMerging.setDisallowChanges(true)
  }

  /**
   * Disables anvil. Should only be used for cases where anvil is explicitly not wanted, such as
   * using Dagger KSP while Anvil doesn't support it.
   */
  @DelicateFoundryGradlePluginApi
  public fun disableAnvil() {
    disableAnvil.setDisallowChanges(true)
  }

  internal fun computeConfig(testFixturesUseDagger: Boolean): DaggerConfig? {
    if (!enabled.get()) return null
    val runtimeOnly = runtimeOnly.get()
    if (runtimeOnly && testFixturesUseDagger) {
      error("Cannot enable dagger in test-fixtures *and* be runtimeOnly(). Please pick one.")
    }
    val enableAnvil = !runtimeOnly && !disableAnvil.get()
    var anvilFactories = true
    var anvilFactoriesOnly = false
    val useDaggerCompiler = useDaggerCompiler.get()
    val alwaysEnableAnvilComponentMerging = !runtimeOnly && alwaysEnableAnvilComponentMerging.get()

    if (useDaggerCompiler) {
      anvilFactories = false
      anvilFactoriesOnly = false
    }

    return DaggerConfig(
      runtimeOnly,
      enableAnvil,
      anvilFactories,
      anvilFactoriesOnly,
      useDaggerCompiler,
      alwaysEnableAnvilComponentMerging,
      testFixturesUseDagger,
    )
  }

  internal data class DaggerConfig(
    val runtimeOnly: Boolean,
    val enableAnvil: Boolean,
    var anvilFactories: Boolean,
    var anvilFactoriesOnly: Boolean,
    val useDaggerCompiler: Boolean,
    val alwaysEnableAnvilComponentMerging: Boolean,
    val testFixturesUseDagger: Boolean,
  )
}

@FoundryExtensionMarker
public abstract class ComposeHandler
@Inject
constructor(
  objects: ObjectFactory,
  globalFoundryProperties: FoundryProperties,
  private val foundryProperties: FoundryProperties,
  versionCatalog: VersionCatalog,
) {

  private val composeBundleAlias =
    globalFoundryProperties.defaultComposeAndroidBundleAlias?.let { alias ->
      versionCatalog.findBundle(alias).orElse(null)
    }
  internal val enabled = objects.property<Boolean>().convention(false)
  internal val multiplatform = objects.property<Boolean>().convention(false)

  private val compilerOptions: ListProperty<String> =
    objects.listProperty<String>().convention(foundryProperties.composeCommonCompilerOptions)

  /**
   * Configures the compiler options for Compose. This is a list of strings that will be passed into
   * the underlying kotlinc invocation. Note that you should _not_ include the plugin prefix, just
   * the simple [key]/[value] options directly.
   *
   * **Do**
   *
   * ```
   * compilerOption("reportsDestination", metricsDir)
   * ```
   *
   * **Don't**
   *
   * ```
   * compilerOption("plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination", metricsDir)
   * ```
   */
  public fun compilerOption(key: String, value: String) {
    compilerOptions.addAll("$key=$value")
  }

  /** @see [AndroidHandler.androidExtension] */
  private var androidExtension: CommonExtension<*, *, *, *, *, *>? = null

  internal fun setAndroidExtension(androidExtension: CommonExtension<*, *, *, *, *, *>?) {
    this.androidExtension = androidExtension
  }

  /**
   * Enables compose compiler metrics for this project. Note this should not be enabled by default
   * and is just for debugging!
   */
  @DelicateFoundryGradlePluginApi
  public fun enableCompilerMetricsForDebugging(
    reportsDestination: File,
    metricsDestination: File = reportsDestination,
  ) {
    compilerOption("reportsDestination", reportsDestination.canonicalPath.toString())
    compilerOption("metricsDestination", metricsDestination.canonicalPath.toString())
  }

  internal fun enable(project: Project, multiplatform: Boolean) {
    enabled.setDisallowChanges(true)
    project.pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
    this.multiplatform.setDisallowChanges(multiplatform)
    if (!multiplatform) {
      val extension =
        checkNotNull(androidExtension) {
          "ComposeHandler must be configured with an Android extension before it can be enabled. Did you apply the Android gradle plugin?"
        }
      extension.apply {
        // Don't need to set buildFeatures.compose = true as that defaults to true if the compose
        // compiler gradle plugin is applied
        if (AndroidPluginVersion.getCurrent() <= AGP_LIVE_LITERALS_MAX_VERSION) {
          composeOptions {
            // Disable live literals by default
            @Suppress("DEPRECATION")
            useLiveLiterals = foundryProperties.composeEnableLiveLiterals
          }
        } else if (foundryProperties.composeEnableLiveLiterals) {
          project.logger.error(
            "Live literals are disabled and deprecated in AGP 8.7+. " +
              "Please remove the `foundry.compose.android.enableLiveLiterals` property."
          )
        }
      }
    }
  }

  internal fun applyTo(project: Project) {
    if (enabled.get()) {
      val extension = project.extensions.getByType<ComposeCompilerGradlePluginExtension>()
      val isMultiplatform = multiplatform.get()
      if (isMultiplatform) {
        project.pluginManager.apply("org.jetbrains.compose")
      } else {
        composeBundleAlias?.let { project.dependencies.add("implementation", it) }
      }

      if (foundryProperties.composeStabilityConfigurationPath.isPresent) {
        extension.stabilityConfigurationFile.setDisallowChanges(
          foundryProperties.composeStabilityConfigurationPath
        )
      }

      // Because the Compose Compiler plugin auto applies common options for us, we need to know
      // about those options and _avoid_ setting them a second time
      val freeOptions = mutableListOf<String>()
      var includeSourceInformation =
        foundryProperties.composeIncludeSourceInformationEverywhereByDefault
      for ((k, v) in compilerOptions.get().map { it.split('=') }) {
        project.logger.debug("Processing compose option $k = $v")
        when (k) {
          "generateFunctionKeyMetaClasses" -> {
            extension.generateFunctionKeyMetaClasses.set(v.toBoolean())
          }

          OPTION_SOURCE_INFORMATION -> {
            includeSourceInformation = v.toBoolean()
          }

          "metricsDestination" -> {
            extension.metricsDestination.set(project.file(v))
          }

          "reportsDestination" -> {
            extension.reportsDestination.set(project.file(v))
          }

          "intrinsicRemember" -> {
            if (v.toBoolean()) {
              extension.featureFlags.add(ComposeFeatureFlag.IntrinsicRemember)
            }
          }

          "nonSkippingGroupOptimization" -> {
            if (v.toBoolean()) {
              extension.featureFlags.add(ComposeFeatureFlag.OptimizeNonSkippingGroups)
            }
          }

          "suppressKotlinVersionCompatibilityCheck" -> {
            error("'suppressKotlinVersionCompatibilityCheck' option is no longer supported")
          }

          "strongSkipping" -> {
            if (v.toBoolean()) {
              extension.featureFlags.add(ComposeFeatureFlag.StrongSkipping)
            }
          }

          "stabilityConfigurationPath" -> {
            error(
              "Use the 'sgp.compose.stabilityConfigurationPath' Gradle property to specify a stability configuration path"
            )
          }

          "traceMarkersEnabled" -> {
            extension.includeTraceMarkers.set(v.toBoolean())
          }

          else -> {
            freeOptions += "$k=$v"
          }
        }
      }

      if (includeSourceInformation) {
        if (androidExtension == null) {
          extension.includeSourceInformation.set(true)
        } else if (foundryProperties.composeUseIncludeInformationWorkaround) {
          freeOptions += "$OPTION_SOURCE_INFORMATION=true"
        }
      }

      if (freeOptions.isNotEmpty()) {
        project.tasks.configureKotlinCompilationTask {
          compilerOptions.freeCompilerArgs.addAll(
            freeOptions.flatMap { listOf("-P", "$COMPOSE_COMPILER_OPTION_PREFIX:$it") }
          )
        }
      }
    }
  }

  private companion object {
    /** Live literals are disabled and deprecated in AGP 8.7+ */
    private val AGP_LIVE_LITERALS_MAX_VERSION = AndroidPluginVersion(8, 6, 0)
    private const val OPTION_SOURCE_INFORMATION = "sourceInformation"
  }
}

@FoundryExtensionMarker
public abstract class AndroidHandler
@Inject
constructor(objects: ObjectFactory, private val foundryProperties: FoundryProperties) {
  internal val libraryHandler = objects.newInstance<FoundryAndroidLibraryExtension>()
  internal val appHandler = objects.newInstance<FoundryAndroidAppExtension>()

  @Suppress("MemberVisibilityCanBePrivate")
  internal val featuresHandler = objects.newInstance<AndroidFeaturesHandler>()

  /** @see [FoundryExtension.androidExtension] */
  private var androidExtension: CommonExtension<*, *, *, *, *, *>? = null
    set(value) {
      field = value
      featuresHandler.setAndroidExtension(value)
    }

  internal val isViewBindingEnabled: Boolean
    get() = androidExtension?.buildFeatures?.viewBinding == true

  internal fun setAndroidExtension(androidExtension: CommonExtension<*, *, *, *, *, *>?) {
    this.androidExtension = androidExtension
  }

  public fun features(action: Action<AndroidFeaturesHandler>) {
    action.execute(featuresHandler)
  }

  public fun library(action: Action<FoundryAndroidLibraryExtension>) {
    action.execute(libraryHandler)
  }

  public fun app(action: Action<FoundryAndroidAppExtension>) {
    action.execute(appHandler)
  }

  internal fun applyTo(project: Project) {
    // Dirty but necessary since the extension isn't configured yet when we call this
    project.afterEvaluate {
      if (featuresHandler.robolectric.getOrElse(false)) {
        checkNotNull(foundryProperties.versions.robolectric) {
          "Robolectric support requested in ${project.path} but no version was specified in the version catalog."
        }
        project.dependencies.apply {
          // For projects using robolectric, we want to make sure they include robolectric-core to
          // ensure robolectric uses our custom dependency resolver and config (which just need
          // to be on the classpath).
          add("testImplementation", FoundryDependencies.Testing.Robolectric.annotations)
          add("testImplementation", FoundryDependencies.Testing.Robolectric.robolectric)
          foundryProperties.robolectricCoreProject?.let { add("testImplementation", project(it)) }
        }
      }
    }
  }
}

@FoundryExtensionMarker
public abstract class AndroidFeaturesHandler @Inject constructor() {
  internal abstract val androidTest: Property<Boolean>
  internal abstract val androidTestExcludeFromFladle: Property<Boolean>
  internal abstract val androidTestAllowedVariants: SetProperty<String>
  internal abstract val robolectric: Property<Boolean>

  /** @see [AndroidHandler.androidExtension] */
  private var androidExtension: CommonExtension<*, *, *, *, *, *>? = null

  internal fun setAndroidExtension(androidExtension: CommonExtension<*, *, *, *, *, *>?) {
    this.androidExtension = androidExtension
  }

  /**
   * Enables android instrumentation tests for this project.
   *
   * @param excludeFromFladle If true, the test will be excluded from Flank/Fladle tests.
   * @param allowedVariants If set, the allowed variants to enable android tests for.
   */
  public fun androidTest(
    excludeFromFladle: Boolean = false,
    allowedVariants: Iterable<String>? = null,
  ) {
    androidTest.setDisallowChanges(true)
    androidTestExcludeFromFladle.setDisallowChanges(excludeFromFladle)
    androidTestAllowedVariants.setDisallowChanges(allowedVariants)
  }

  /** Enables robolectric for this project. */
  // In the future, we may want to add an enum for picking which shadows/artifacts
  public fun robolectric() {
    // Required for Robolectric to work.
    androidExtension!!.testOptions.unitTests.isIncludeAndroidResources = true
    robolectric.setDisallowChanges(true)
  }

  /**
   * **LIBRARIES ONLY**
   *
   * Enables android resources in this library and enforces use of the given [prefix] for all
   * resources.
   */
  public fun resources(prefix: String) {
    val libraryExtension =
      androidExtension as? LibraryExtension
        ?: error("foundry.android.features.resources() is only applicable in libraries!")
    libraryExtension.resourcePrefix = prefix
    libraryExtension.buildFeatures { androidResources = true }
  }
}

@FoundryExtensionMarker
public abstract class FoundryAndroidLibraryExtension {
  // Left as a toe-hold for the future
}

@FoundryExtensionMarker
public abstract class FoundryAndroidAppExtension {
  internal var allowlistAction: Action<PermissionAllowlistConfigurer>? = null

  /**
   * Configures a permissions allowlist on a per-variant basis with a VariantFilter-esque API.
   *
   * Example:
   * ```
   * foundry {
   *   permissionAllowlist {
   *     if (buildType.name == "release") {
   *       setAllowlistFile(file('path/to/allowlist.txt'))
   *     }
   *   }
   * }
   * ```
   */
  public fun permissionAllowlist(factory: Action<PermissionAllowlistConfigurer>) {
    allowlistAction = factory
  }
}
