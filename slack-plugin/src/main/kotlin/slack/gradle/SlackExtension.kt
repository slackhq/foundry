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

package slack.gradle

import com.android.build.api.dsl.CommonExtension
import com.squareup.anvil.plugin.AnvilExtension
import dev.zacsweers.moshix.ir.gradle.MoshiPluginExtension
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.domainObjectSet
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.plugin.KaptExtension
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME
import slack.gradle.agp.PermissionAllowlistConfigurer
import slack.gradle.dependencies.SlackDependencies

@DslMarker public annotation class SlackExtensionMarker

@SlackExtensionMarker
public abstract class SlackExtension
@Inject
constructor(
  objects: ObjectFactory,
  globalSlackProperties: SlackProperties,
  private val slackProperties: SlackProperties,
  versionCatalog: VersionCatalog
) {
  internal val androidHandler =
    objects.newInstance<AndroidHandler>(globalSlackProperties, slackProperties, versionCatalog)
  internal val featuresHandler = objects.newInstance<FeaturesHandler>()

  public fun android(action: Action<AndroidHandler>) {
    action.execute(androidHandler)
  }

  public fun features(action: Action<FeaturesHandler>) {
    action.execute(featuresHandler)
  }

  internal fun applyTo(project: Project) {
    val logVerbose = slackProperties.slackExtensionVerbose
    // Dirty but necessary since the extension isn't configured yet when we call this
    project.afterEvaluate {
      var kaptRequired = false
      var naptRequired = false
      val avMoshiEnabled = featuresHandler.avExtensionMoshi.getOrElse(false)
      val moshiCodegenEnabled = featuresHandler.moshiHandler.moshiCodegen.getOrElse(false)
      val moshiSealedCodegenEnabled = featuresHandler.moshiHandler.sealedCodegen.getOrElse(false)
      val allowKsp = slackProperties.allowKsp
      val allowMoshiIr = slackProperties.allowMoshiIr
      val allowNapt = slackProperties.allowNapt

      /** Marks this project as needing kapt code gen. */
      fun markKaptNeeded(source: String) {
        if (allowNapt) {
          naptRequired = true
          // Apply napt for them
          pluginManager.apply("com.sergei-lapin.napt")
        } else {
          kaptRequired = true
          // Apply kapt for them
          pluginManager.apply("org.jetbrains.kotlin.kapt")
        }
        if (logVerbose) {
          logger.lifecycle(
            """
            [kapt/napt Config]
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
          configure<MoshiPluginExtension> { this.enableSealed.set(true) }
        }
      }

      fun aptConfiguration(): String {
        return if (isKotlin && !naptRequired) {
          "kapt"
        } else {
          "annotationProcessor"
        }
      }

      // Dagger is configured first. If Dagger's compilers are present,
      // everything else needs to also use kapt!
      val daggerConfig = featuresHandler.daggerHandler.computeConfig()
      if (daggerConfig != null) {
        dependencies.add("implementation", SlackDependencies.Dagger.dagger)
        dependencies.add("implementation", SlackDependencies.javaxInject)

        if (daggerConfig.runtimeOnly) {
          dependencies.add("compileOnly", SlackDependencies.Anvil.annotations)
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
          pluginManager.apply("com.squareup.anvil")
          configure<AnvilExtension> {
            generateDaggerFactories.set(daggerConfig.anvilFactories)
            generateDaggerFactoriesOnly.set(daggerConfig.anvilFactoriesOnly)
          }

          val runtimeProjects =
            slackProperties.anvilRuntimeProjects?.splitToSequence(";")?.toSet().orEmpty()

          for (runtimeProject in runtimeProjects) {
            dependencies.add("implementation", project(runtimeProject))
          }

          val generatorProjects =
            buildSet<Any> {
              addAll(
                slackProperties.anvilGeneratorProjects
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

        if (!daggerConfig.runtimeOnly && daggerConfig.useDaggerCompiler) {
          markKaptNeeded("Dagger compiler")
          dependencies.add(aptConfiguration(), SlackDependencies.Dagger.compiler)
        }
      }

      if (featuresHandler.autoValue.getOrElse(false)) {
        markKaptNeeded("AutoValue")
        dependencies.add("compileOnly", SlackDependencies.Auto.Value.annotations)
        dependencies.add(aptConfiguration(), SlackDependencies.Auto.Value.autovalue)
        if (avMoshiEnabled) {
          dependencies.add("implementation", SlackDependencies.Auto.Value.Moshi.runtime)
          dependencies.add(aptConfiguration(), SlackDependencies.Auto.Value.Moshi.extension)
        }
        if (featuresHandler.avExtensionParcel.getOrElse(false)) {
          dependencies.add("implementation", SlackDependencies.Auto.Value.Parcel.adapter)
          dependencies.add(aptConfiguration(), SlackDependencies.Auto.Value.Parcel.extension)
        }
        if (featuresHandler.avExtensionWith.getOrElse(false)) {
          dependencies.add(aptConfiguration(), SlackDependencies.Auto.Value.with)
        }
        if (featuresHandler.avExtensionKotlin.getOrElse(false)) {
          dependencies.add(aptConfiguration(), SlackDependencies.Auto.Value.kotlin)
          configure<KaptExtension> { arguments { arg("avkSrc", project.file("src/main/java")) } }
        }
      }

      if (featuresHandler.autoService.getOrElse(false)) {
        if (allowKsp) {
          markKspNeeded("AutoService")
          dependencies.add("implementation", SlackDependencies.Auto.Service.annotations)
          dependencies.add("ksp", SlackDependencies.Auto.Service.ksp)
        } else {
          markKaptNeeded("AutoService")
          dependencies.add("compileOnly", SlackDependencies.Auto.Service.annotations)
          dependencies.add(aptConfiguration(), SlackDependencies.Auto.Service.autoservice)
        }
      }

      if (featuresHandler.incap.getOrElse(false)) {
        markKaptNeeded("Incap")
        dependencies.add("compileOnly", SlackDependencies.Incap.incap)
        dependencies.add(aptConfiguration(), SlackDependencies.Incap.processor)
      }

      if (featuresHandler.redacted.getOrElse(false)) {
        apply(plugin = "dev.zacsweers.redacted")
      }

      if (featuresHandler.moshiHandler.moshi.getOrElse(false)) {
        dependencies.add("implementation", SlackDependencies.Moshi.moshi)
        if (moshiCodegenEnabled) {
          if (allowMoshiIr) {
            markMoshiGradleNeeded("Moshi code gen", false)
          } else if (allowKsp) {
            markKspNeeded("Moshi code gen")
            dependencies.add("ksp", SlackDependencies.Moshi.codeGen)
          } else {
            markKaptNeeded("Moshi code gen")
            dependencies.add(aptConfiguration(), SlackDependencies.Moshi.codeGen)
          }
        }
        if (featuresHandler.moshiHandler.moshiAdapters.getOrElse(false)) {
          dependencies.add("implementation", SlackDependencies.Moshi.adapters)
        }
        if (featuresHandler.moshiHandler.moshiKotlinReflect.getOrElse(false)) {
          dependencies.add("implementation", SlackDependencies.Moshi.kotlinReflect)
        }
        if (featuresHandler.moshiHandler.moshixAdapters.getOrElse(false)) {
          dependencies.add("implementation", SlackDependencies.Moshi.MoshiX.adapters)
        }
        if (featuresHandler.moshiHandler.moshixMetadataReflect.getOrElse(false)) {
          dependencies.add("implementation", SlackDependencies.Moshi.MoshiX.metadataReflect)
        }
        if (featuresHandler.moshiHandler.lazyAdapters.getOrElse(false)) {
          dependencies.add("implementation", SlackDependencies.Moshi.lazyAdapters)
        }
        if (featuresHandler.moshiHandler.sealed.getOrElse(false)) {
          dependencies.add("implementation", SlackDependencies.Moshi.MoshiX.Sealed.runtime)
          if (moshiSealedCodegenEnabled) {
            if (allowMoshiIr) {
              markMoshiGradleNeeded("Moshi sealed codegen", enableSealed = true)
            } else if (allowKsp) {
              markKspNeeded("Moshi sealed codegen")
              dependencies.add("ksp", SlackDependencies.Moshi.MoshiX.Sealed.codegen)
            } else {
              markKaptNeeded("Moshi sealed codegen")
              dependencies.add(aptConfiguration(), SlackDependencies.Moshi.MoshiX.Sealed.codegen)
            }
          }
          if (featuresHandler.moshiHandler.sealedReflect.getOrElse(false)) {
            dependencies.add("implementation", SlackDependencies.Moshi.MoshiX.Sealed.reflect)
          }
          if (featuresHandler.moshiHandler.sealedMetadataReflect.getOrElse(false)) {
            dependencies.add(
              "implementation",
              SlackDependencies.Moshi.MoshiX.Sealed.metadataReflect
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
        configure<AnvilExtension> { disableComponentMerging.set(true) }
      }
    }
  }
}

@SlackExtensionMarker
public abstract class FeaturesHandler @Inject constructor(objects: ObjectFactory) {
  // Dagger features
  internal val daggerHandler = objects.newInstance<DaggerHandler>()

  /** Enables AutoService on this project. */
  internal abstract val autoService: Property<Boolean>

  /** Enables InCap on this project. */
  internal abstract val incap: Property<Boolean>

  /** Enables redacted-compiler-plugin on this project. */
  internal abstract val redacted: Property<Boolean>

  // AutoValue
  internal abstract val autoValue: Property<Boolean>
  internal abstract val avExtensionMoshi: Property<Boolean>
  internal abstract val avExtensionParcel: Property<Boolean>
  internal abstract val avExtensionWith: Property<Boolean>
  internal abstract val avExtensionKotlin: Property<Boolean>

  // Moshi
  internal val moshiHandler = objects.newInstance<MoshiHandler>()

  /**
   * Enables dagger for this project.
   *
   * @param action optional block for extra configuration, such as anvil generators or android.
   */
  public fun dagger(action: Action<DaggerHandler>? = null) {
    daggerHandler.enabled.set(true)
    action?.execute(daggerHandler)
  }

  /**
   * Enables dagger for this project.
   *
   * @param enableComponents enables dagger components in this project, which in turn imposes use
   * ```
   *                         of the dagger compiler (slower!)
   * @param projectHasJavaInjections
   * ```
   * indicates if this project has injected _Java_ files. This means
   * ```
   *                                 any Java file with `@Inject` or `@AssistedInject`. This imposes
   *                                 use of the dagger compiler (slower!) because Anvil only
   *                                 processes Kotlin files.
   * @param action
   * ```
   * optional block for extra configuration, such as anvil generators or android.
   */
  @DelicateSlackPluginApi
  public fun dagger(
    enableComponents: Boolean = false,
    projectHasJavaInjections: Boolean = false,
    action: Action<DaggerHandler>? = null
  ) {
    check(enableComponents || projectHasJavaInjections) {
      "This function should not be called with both enableComponents and projectHasJavaInjections set to false. Either remove these parameters or call a more appropriate non-delicate dagger() overload."
    }
    daggerHandler.enabled.set(true)
    daggerHandler.useDaggerCompiler.set(enableComponents || projectHasJavaInjections)
    action?.execute(daggerHandler)
  }

  /** Adds dagger's runtime as dependencies to this but runs no code generation. */
  public fun daggerRuntimeOnly() {
    daggerHandler.enabled.set(true)
    daggerHandler.runtimeOnly.set(true)
  }

  /**
   * Enables AutoValue for this project.
   *
   * @param moshi Enables auto-value-moshi
   * @param parcel Enables auto-value-parcel
   * @param with Enables auto-value-with
   */
  @OptIn(DelicateSlackPluginApi::class)
  public fun autoValue(
    moshi: Boolean = false,
    parcel: Boolean = false,
    with: Boolean = false,
  ) {
    autoValue(moshi, parcel, with, kotlin = false)
  }

  /**
   * Enables AutoValue for this project.
   *
   * @param moshi Enables auto-value-moshi
   * @param parcel Enables auto-value-parcel
   * @param with Enables auto-value-with
   * @param kotlin Enables auto-value-kotlin. THIS SHOULD ONLY BE TEMPORARY FOR MIGRATION PURPOSES!
   */
  @DelicateSlackPluginApi
  public fun autoValue(
    moshi: Boolean = false,
    parcel: Boolean = false,
    with: Boolean = false,
    kotlin: Boolean = false
  ) {
    autoValue.set(true)
    avExtensionParcel.set(parcel)
    avExtensionMoshi.set(moshi)
    avExtensionWith.set(with)
    avExtensionKotlin.set(kotlin)
  }

  /**
   * Enables Moshi for this project.
   *
   * @param codegen Enables codegen.
   * @param adapters Enables moshi-adapters.
   * @param kotlinReflect Enables kotlin-reflect-based support. Should only be used in unit tests or
   * CLIs!
   * @param action Optional extra configuration for other Moshi libraries, such as MoshiX.
   */
  public fun moshi(
    codegen: Boolean,
    adapters: Boolean = false,
    kotlinReflect: Boolean = false,
    action: Action<MoshiHandler> = Action {}
  ) {
    action.execute(moshiHandler)
    moshiHandler.moshi.set(true)
    moshiHandler.moshiAdapters.set(adapters)
    moshiHandler.moshiCodegen.set(codegen)
    moshiHandler.moshiKotlinReflect.set(kotlinReflect)
  }

  /** Enables AutoService on this project. */
  public fun autoService() {
    autoService.set(true)
  }

  /** Enables InCap on this project. */
  public fun incap() {
    incap.set(true)
  }

  /** Enables redacted-compiler-plugin on this project. */
  public fun redacted() {
    redacted.set(true)
  }
}

@SlackExtensionMarker
@Suppress("UnnecessaryAbstractClass")
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
  public fun moshix(
    adapters: Boolean,
    metadataReflect: Boolean = false,
  ) {
    moshi.set(true)
    moshixAdapters.set(adapters)
    moshixMetadataReflect.set(metadataReflect)
  }

  /**
   * Enables MoshiX-sealed on this project. This is used for polymorphic types.
   *
   * @param codegen Enables codegen.
   * @param kotlinReflect Enables kotlin-reflect-based support. Should only be used in unit tests or
   * CLIs!
   * @param metadataReflect Enables metadata-based reflection support. Should only be used in unit
   * tests or CLIs!
   */
  public fun sealed(
    codegen: Boolean,
    kotlinReflect: Boolean = false,
    metadataReflect: Boolean = false
  ) {
    moshi.set(true)
    sealed.set(true)
    sealedCodegen.set(codegen)
    sealedReflect.set(kotlinReflect)
    sealedMetadataReflect.set(metadataReflect)
  }

  /** Enables [moshi-lazy-adapters](https://github.com/serj-lotutovici/moshi-lazy-adapters). */
  public fun lazyAdapters() {
    lazyAdapters.set(true)
  }
}

@SlackExtensionMarker
@Suppress("UnnecessaryAbstractClass")
public abstract class DaggerHandler @Inject constructor(objects: ObjectFactory) {
  internal val enabled: Property<Boolean> = objects.property<Boolean>().convention(false)
  internal val useDaggerCompiler: Property<Boolean> = objects.property<Boolean>().convention(false)
  internal val runtimeOnly: Property<Boolean> = objects.property<Boolean>().convention(false)
  internal val alwaysEnableAnvilComponentMerging: Property<Boolean> =
    objects.property<Boolean>().convention(false)
  internal val anvilGenerators = objects.domainObjectSet(Any::class)

  /**
   * Dependencies for Anvil generators that should be added. These should be in the same form as
   * they would be added to regular project dependencies.
   *
   * ```
   * slack {
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
  @DelicateSlackPluginApi
  public fun alwaysEnableAnvilComponentMerging() {
    alwaysEnableAnvilComponentMerging.set(true)
  }

  internal fun computeConfig(): DaggerConfig? {
    if (!enabled.get()) return null
    val runtimeOnly = runtimeOnly.get()
    val enableAnvil = !runtimeOnly
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
      alwaysEnableAnvilComponentMerging
    )
  }

  internal data class DaggerConfig(
    val runtimeOnly: Boolean,
    val enableAnvil: Boolean,
    var anvilFactories: Boolean,
    var anvilFactoriesOnly: Boolean,
    val useDaggerCompiler: Boolean,
    val alwaysEnableAnvilComponentMerging: Boolean
  )
}

@SlackExtensionMarker
@Suppress("UnnecessaryAbstractClass")
public abstract class ComposeHandler
@Inject
constructor(
  objects: ObjectFactory,
  globalSlackProperties: SlackProperties,
  slackProperties: SlackProperties,
  versionCatalog: VersionCatalog
) {

  private val composeBundleAlias =
    globalSlackProperties.defaultComposeAndroidBundleAlias?.let { alias ->
      versionCatalog.findBundle(alias).orElse(null)
    }
  private val composeCompilerVersion by lazy {
    slackProperties.versions.composeCompiler
      ?: error("Missing `compose-compiler` version in catalog")
  }
  internal val enabled = objects.property<Boolean>().convention(false)

  /**
   * This is weird! Due to the non-property nature of the AGP buildFeatures and composeOptions DSLs,
   * we can't lazily chain their values to our own extension's properties. Because of this, we
   * lazily set this instance from [StandardProjectConfigurations] during Android extension
   * evaluation and then make calls to [enable] directly set the values on this instance. Ideally we
   * could eventually remove this if/when AGP finally makes these properties lazy.
   */
  internal var androidExtension: CommonExtension<*, *, *, *>? = null

  internal fun enable() {
    val extension =
      checkNotNull(androidExtension) {
        "ComposeHandler must be configured with an Android extension before it can be enabled. Did you apply the Android gradle plugin?"
      }
    enabled.set(true)
    enabled.disallowChanges()
    extension.apply {
      buildFeatures { compose = true }
      composeOptions { kotlinCompilerExtensionVersion = composeCompilerVersion }
    }
  }

  internal fun applyTo(project: Project) {
    if (enabled.getOrElse(false)) {
      composeBundleAlias?.let { project.dependencies.add("implementation", it) }
      project.pluginManager.withPlugin("org.jetbrains.compose") {
        project.dependencies {
          add(
            PLUGIN_CLASSPATH_CONFIGURATION_NAME,
            "androidx.compose.compiler:compiler:$composeCompilerVersion"
          )
        }
      }
    }
  }
}

@SlackExtensionMarker
@Suppress("UnnecessaryAbstractClass")
public abstract class AndroidHandler
@Inject
constructor(
  objects: ObjectFactory,
  globalSlackProperties: SlackProperties,
  private val slackProperties: SlackProperties,
  versionCatalog: VersionCatalog
) {
  internal val libraryHandler = objects.newInstance<SlackAndroidLibraryExtension>()
  internal val appHandler = objects.newInstance<SlackAndroidAppExtension>()

  @Suppress("MemberVisibilityCanBePrivate")
  internal val featuresHandler =
    objects.newInstance<AndroidFeaturesHandler>(
      globalSlackProperties,
      slackProperties,
      versionCatalog
    )

  public fun features(action: Action<AndroidFeaturesHandler>) {
    action.execute(featuresHandler)
  }

  public fun library(action: Action<SlackAndroidLibraryExtension>) {
    action.execute(libraryHandler)
  }

  public fun app(action: Action<SlackAndroidAppExtension>) {
    action.execute(appHandler)
  }

  internal fun applyTo(project: Project) {
    // Dirty but necessary since the extension isn't configured yet when we call this
    project.afterEvaluate {
      if (featuresHandler.robolectric.getOrElse(false)) {
        project.dependencies {
          // For projects using robolectric, we want to make sure they include robolectric-core to
          // ensure robolectric uses our custom dependency resolver and config (which just need
          // to be on the classpath).
          add("testImplementation", SlackDependencies.Testing.Robolectric.annotations)
          add("testImplementation", SlackDependencies.Testing.Robolectric.robolectric)
          add("testImplementation", slackProperties.robolectricCoreProject)
        }
        // Robolectric 4.9+ requires these --add-opens options.
        // https://github.com/robolectric/robolectric/issues/7456
        project.tasks.withType<Test>().configureEach {
          jvmArgs(
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
          )
        }
      }

      featuresHandler.composeHandler.applyTo(project)
    }
  }
}

@SlackExtensionMarker
public abstract class AndroidFeaturesHandler
@Inject
constructor(
  objects: ObjectFactory,
  globalSlackProperties: SlackProperties,
  slackProperties: SlackProperties,
  versionCatalog: VersionCatalog
) {
  internal abstract val androidTest: Property<Boolean>
  internal abstract val androidTestExcludeFromFladle: Property<Boolean>
  internal abstract val androidTestAllowedVariants: SetProperty<String>
  internal abstract val robolectric: Property<Boolean>

  // Compose features
  internal val composeHandler =
    objects.newInstance<ComposeHandler>(globalSlackProperties, slackProperties, versionCatalog)

  /**
   * Enables android instrumentation tests for this project.
   *
   * @param excludeFromFladle If true, the test will be excluded from Flank/Fladle tests.
   * @param allowedVariants If set, the allowed variants to enable android tests for.
   */
  public fun androidTest(
    excludeFromFladle: Boolean = false,
    allowedVariants: Iterable<String>? = null
  ) {
    androidTest.set(true)
    androidTestExcludeFromFladle.set(excludeFromFladle)
    androidTestAllowedVariants.set(allowedVariants)
    androidTest.disallowChanges()
    androidTestExcludeFromFladle.disallowChanges()
    androidTestAllowedVariants.disallowChanges()
  }

  /** Enables robolectric for this project. */
  // In the future, we may want to add an enum for picking which shadows/artifacts
  public fun robolectric() {
    robolectric.set(true)
  }

  /**
   * Enables Compose for this project and applies any version catalog bundle dependencies defined by
   * [SlackProperties.defaultComposeAndroidBundleAlias].
   */
  public fun compose() {
    compose {
      // No further configuration right now
    }
  }

  private fun compose(action: Action<ComposeHandler>) {
    composeHandler.enable()
    action.execute(composeHandler)
  }
}

@SlackExtensionMarker
public abstract class SlackAndroidLibraryExtension {
  // Left as a toe-hold for the future
}

@SlackExtensionMarker
public abstract class SlackAndroidAppExtension {
  internal var allowlistAction: Action<PermissionAllowlistConfigurer>? = null

  /**
   * Configures a permissions allowlist on a per-variant basis with a VariantFilter-esque API.
   *
   * Example:
   * ```
   * slack {
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
