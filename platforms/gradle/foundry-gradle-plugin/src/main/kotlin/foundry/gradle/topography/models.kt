package foundry.gradle.topography

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
public data class ModuleTopography(
  val name: String,
  val gradlePath: String,
  val features: Set<ModuleFeature>,
  val plugins: Set<String>,
)

@JsonClass(generateAdapter = true)
public data class ModuleFeature(
  val name: String,
  val removalMessage: String,
  /** Generated sources root dir, if any. Note that descendants are checked */
  val generatedSourcesDir: String? = null,
  val generatedSourcesExtensions: Set<String> = emptySet(),
  val matchingText: Set<String> = emptySet(),
  val matchingAnnotationsExtensions: Set<String> = emptySet(),
  /** If specified, looks for any sources in this dir */
  val matchingSourcesDir: String? = null,
)

// TODO eventually move these to JSON configs
internal object Features {
  internal val AndroidTest =
    ModuleFeature(
      name = "androidTest",
      removalMessage = "Remove foundry.android.features.androidTest from your build file",
      matchingSourcesDir = "src/androidTest",
    )

  internal val Robolectric =
    ModuleFeature(
      name = "robolectric",
      removalMessage = "Remove foundry.android.features.robolectric from your build file",
      matchingSourcesDir = "src/test",
    )

  internal val Compose =
    ModuleFeature(
      name = "compose",
      removalMessage = "Remove foundry.features.compose from your build file",
      matchingText = setOf("@Composable"),
      matchingAnnotationsExtensions = setOf("kt"),
    )

  internal val Dagger =
    ModuleFeature(
      name = "dagger",
      removalMessage = "Remove foundry.features.dagger from your build file",
      matchingText =
        setOf(
          "@Inject",
          "@AssistedInject",
          "@ContributesTo",
          "@ContributesBinding",
          "@ContributesMultibinding",
          "@Module",
          "@Component",
          "@Subcomponent",
          "@MergeComponent",
          "@MergeSubcomponent",
          "@MergeModules",
          "@MergeInterfaces",
          "@ContributesSubcomponent",
          "import dagger.",
        ),
      matchingAnnotationsExtensions = setOf("kt", "java"),
    )

  internal val DaggerCompiler =
    ModuleFeature(
      name = "dagger-compiler",
      removalMessage = "Remove foundry.features.dagger.mergeComponents from your build file",
      matchingText =
        setOf(
          "@Component",
          "@Subcomponent",
          "@MergeComponent",
          "@MergeSubcomponent",
          "@MergeModules",
          "@MergeInterfaces",
          "@ContributesSubcomponent",
          // TODO configurable custom annotations? Or we just need to search generated sources too
          "@CircuitInject",
          "@FeatureFlags",
          "@GuinnessApi",
          "@SlackRemotePreferences",
          "@WorkRequestIn",
        ),
      matchingAnnotationsExtensions = setOf("kt", "java"),
      generatedSourcesDir = "build/generated/source/kapt",
      generatedSourcesExtensions = setOf("java"),
    )

  internal val MoshiCodeGen =
    ModuleFeature(
      name = "moshi-codegen",
      removalMessage = "Remove foundry.features.moshi.codeGen from your build file",
      matchingText = setOf("@JsonClass"),
      matchingAnnotationsExtensions = setOf("kt"),
    )

  internal val CircuitInject =
    ModuleFeature(
      name = "circuit-inject",
      removalMessage = "Remove foundry.features.circuit.codeGen from your build file",
      matchingText = setOf("@CircuitInject"),
      matchingAnnotationsExtensions = setOf("kt"),
    )

  internal val Parcelize =
    ModuleFeature(
      name = "parcelize",
      removalMessage = "Remove the parcelize plugin from your build file",
      matchingText = setOf("@Parcelize"),
      matchingAnnotationsExtensions = setOf("kt"),
    )

  internal val Ksp =
    ModuleFeature(
      name = "ksp",
      removalMessage = "Remove the KSP plugin (or whatever Foundry feature is requesting it)",
      generatedSourcesDir = "build/generated/ksp",
      // Don't specify extensions because KAPT can generate anything into resources
    )

  internal val Kapt =
    ModuleFeature(
      name = "kapt",
      removalMessage = "Remove the KAPT plugin (or whatever Foundry feature is requesting it)",
      generatedSourcesDir = "build/generated/source/kapt",
      // Don't specify extensions because KSP can generate anything into resources
    )

  internal val ViewBinding =
    ModuleFeature(
      name = "viewbinding",
      removalMessage = "Remove android.buildFeatures.viewBinding from your build file",
      generatedSourcesDir = "build/generated/data_binding_base_class_source_out",
      generatedSourcesExtensions = setOf("java"),
    )
}
