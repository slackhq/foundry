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
package foundry.gradle.topography

import kotlin.reflect.full.declaredMemberProperties

internal object DefaultFeatures {
  private val cachedValue by lazy {
    DefaultFeatures::class
      .declaredMemberProperties
      .filter { it.returnType.classifier == ModuleFeature::class }
      .associate {
        val feature = it.get(DefaultFeatures) as ModuleFeature
        feature.name to feature
      }
  }

  fun load(): Map<String, ModuleFeature> = cachedValue

  internal val AndroidTest =
    ModuleFeature(
      name = "androidTest",
      explanation =
        "The `androidTest()` feature was requested but no sources were found at `src/androidTest/**`",
      advice = "Remove `foundry.android.features.androidTest` from your build file",
      removalPatterns = setOf("\\bandroidTest\\(\\)".toRegex()),
      matchingSourcesDir = "src/androidTest",
    )

  internal val Robolectric =
    ModuleFeature(
      name = "robolectric",
      explanation =
        "The `robolectric()` feature was requested but no sources were found at `src/test/**`",
      advice = "Remove `foundry.android.features.robolectric` from your build file",
      removalPatterns = setOf("\\brobolectric\\(\\)".toRegex()),
      matchingSourcesDir = "src/test",
    )

  internal val Compose =
    ModuleFeature(
      name = "compose",
      explanation =
        "The `compose()` feature (and thus compose-compiler) was requested but no `@Composable` annotations were found in sources",
      advice =
        "Remove `foundry.features.compose` from your build file or use `foundry.features.composeRuntimeOnly()`",
      removalPatterns = setOf("\\bcompose\\(\\)".toRegex()),
      matchingText = setOf("@Composable", "setContent {"),
      matchingTextFileExtensions = setOf("kt"),
    )

  internal val DaggerCompiler =
    ModuleFeature(
      name = "dagger-compiler",
      explanation =
        "The `mergeComponents()` feature (and thus dagger-compiler/KAPT) was requested but no corresponding Merge*/*Component annotations were found in sources",
      advice = "Remove `foundry.features.dagger.mergeComponents` from your build file",
      removalPatterns = setOf("\\bmergeComponents\\(\\)".toRegex()),
      matchingText =
        setOf(
          "@Component",
          "@Subcomponent",
          "@MergeComponent",
          "@MergeSubcomponent",
          "@MergeModules",
          "@MergeInterfaces",
          "@ContributesSubcomponent",
        ),
      matchingTextFileExtensions = setOf("kt", "java"),
      generatedSourcesDir = "build/generated/source/kapt",
      generatedSourcesExtensions = setOf("java"),
    )

  internal val Dagger =
    ModuleFeature(
      name = "dagger",
      explanation =
        "The `dagger()` feature (and thus Anvil/KSP) was requested but no Dagger/Anvil annotations were found in sources",
      advice = "Remove `foundry.features.dagger` from your build file",
      removalPatterns = setOf("\\bdagger\\(\\)".toRegex()),
      matchingText =
        buildSet {
          addAll(DaggerCompiler.matchingText)
          addAll(
            setOf(
              "@Inject",
              "@AssistedInject",
              "@ContributesTo",
              "@ContributesBinding",
              "@ContributesMultibinding",
              "@Module",
              "import dagger.",
              // TODO configurable custom annotations? Or we just need to search generated sources
              // too
              "@CircuitInject",
              "@FeatureFlags",
              "@GuinnessApi",
              "@SlackRemotePreferences",
              "@WorkRequestIn",
            )
          )
        },
      matchingTextFileExtensions = setOf("kt", "java"),
    )

  internal val MoshiCodeGen =
    ModuleFeature(
      name = "moshi-codegen",
      explanation =
        "The `moshi(codeGen = true)` feature (and thus the moshi-ir compiler plugin) was requested but no `@JsonClass` annotations were found in sources",
      advice = "Remove `foundry.features.moshi.codeGen` from your build file",
      removalPatterns = null,
      matchingText = setOf("@JsonClass"),
      matchingTextFileExtensions = setOf("kt"),
    )

  internal val CircuitInject =
    ModuleFeature(
      name = "circuit-inject",
      explanation =
        "The `circuit(codegen = true)` feature (and thus the KSP) was requested but no `@CircuitInject` annotations were found in sources",
      advice =
        "Remove `foundry.features.circuit.codegen` from your build file or set codegen to false (i.e. `circuit(codegen = false)`)",
      removalPatterns = null,
      matchingText = setOf("@CircuitInject"),
      matchingTextFileExtensions = setOf("kt"),
    )

  internal val Parcelize =
    ModuleFeature(
      name = "parcelize",
      explanation =
        "The parcelize plugin (and thus its compiler plugin) was requested but no `@Parcelize` annotations were found in sources",
      advice = "Remove the parcelize plugin from your build file",
      removalPatterns =
        setOf("\\balias\\(libs\\.plugins\\.kotlin\\.plugin\\.parcelize\\)".toRegex()),
      matchingText = setOf("@Parcelize"),
      matchingTextFileExtensions = setOf("kt"),
      matchingPlugin = "org.jetbrains.kotlin.plugin.parcelize",
    )

  internal val Ksp =
    ModuleFeature(
      name = "ksp",
      explanation =
        "The KSP plugin was requested but no generated files were found in `build/generated/ksp`",
      advice = "Remove the KSP plugin (or whatever Foundry feature is requesting it)",
      removalPatterns =
        setOf("\\balias\\(libs\\.plugins\\.ksp\\)".toRegex(), "\\bksp\\([a-zA-Z.-]*\\)".toRegex()),
      generatedSourcesDir = "build/generated/ksp",
      matchingPlugin = "com.google.devtools.ksp",
      // Don't specify extensions because KAPT can generate anything into resources
    )

  internal val Kapt =
    ModuleFeature(
      name = "kapt",
      explanation =
        "The KAPT plugin was requested but no generated files were found in `build/generated/source/kapt`",
      advice = "Remove the KAPT plugin (or whatever Foundry feature is requesting it)",
      removalPatterns =
        setOf(
          "\\balias\\(libs\\.plugins\\.kotlin\\.kapt\\)".toRegex(),
          "\\bkapt\\([a-zA-Z.-]*\\)".toRegex(),
        ),
      generatedSourcesDir = "build/generated/source/kapt",
      matchingPlugin = "org.jetbrains.kotlin.kapt",
      // Don't specify file extensions because KSP can generate anything into resources
    )

  internal val ViewBinding =
    ModuleFeature(
      name = "viewbinding",
      explanation =
        "Android ViewBinding was enabled but no generated viewbinding sources were found in `build/generated/data_binding_base_class_source_out`",
      advice = "Remove android.buildFeatures.viewBinding from your build file",
      removalPatterns = setOf("\\bviewBinding = true".toRegex()),
      generatedSourcesDir = "build/generated/data_binding_base_class_source_out",
      generatedSourcesExtensions = setOf("java"),
    )
}
