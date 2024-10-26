package foundry.gradle.model

import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.adapter
import foundry.cli.walkEachFile
import foundry.gradle.FoundryExtension
import foundry.gradle.properties.setDisallowChanges
import foundry.gradle.register
import foundry.gradle.util.JsonTools.MOSHI
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.extension
import kotlin.io.path.useLines
import okio.buffer
import okio.sink
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

@CacheableTask
public abstract class ModuleTopographyTask : DefaultTask() {
  @get:Input public abstract val projectName: Property<String>

  @get:Input public abstract val projectPath: Property<String>

  @get:InputDirectory
  @get:SkipWhenEmpty
  @get:PathSensitive(PathSensitivity.RELATIVE)
  public abstract val srcsDirProperty: DirectoryProperty

  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  public abstract val buildDirProperty: DirectoryProperty

  @get:Input @get:Optional public abstract val moshiCodeGenEnabled: Property<Boolean>
  @get:Input @get:Optional public abstract val circuitCodeGenEnabled: Property<Boolean>

  @get:Input @get:Optional public abstract val daggerEnabled: Property<Boolean>

  @get:Input @get:Optional public abstract val daggerCompilerEnabled: Property<Boolean>

  @get:Input @get:Optional public abstract val viewBindingEnabled: Property<Boolean>

  @get:Input @get:Optional public abstract val composeEnabled: Property<Boolean>

  @get:Input @get:Optional public abstract val androidTestEnabled: Property<Boolean>

  @get:Input @get:Optional public abstract val kaptEnabled: Property<Boolean>

  @get:Input @get:Optional public abstract val kspEnabled: Property<Boolean>

  // TODO source DAGP files to check usage of Android APIs?

  @get:OutputFile public abstract val topographyOutputFile: RegularFileProperty

  @get:OutputFile public abstract val featuresToRemoveOutputFile: RegularFileProperty

  init {
    group = "foundry"
  }

  @OptIn(ExperimentalPathApi::class)
  @TaskAction
  public fun compute() {
    val featuresEnabled = mutableSetOf<ModuleFeature>()
    val featuresToRemove = mutableSetOf<ModuleFeature>()

    val srcsDir = srcsDirProperty.asFile.get().toPath()

    if (androidTestEnabled.getOrElse(false)) {
      featuresEnabled += Features.AndroidTest
      if (srcsDir.resolve("androidTest").walkEachFile().none()) {
        featuresToRemove += Features.AndroidTest
      }
    }

    val generatedSourcesDir = buildDirProperty.asFile.get().toPath().resolve("generated")

    if (kspEnabled.getOrElse(false)) {
      featuresEnabled += Features.Ksp
      if (generatedSourcesDir.resolve("ksp").walkEachFile().none()) {
        featuresToRemove += Features.Ksp
      }
    }
    if (kaptEnabled.getOrElse(false)) {
      featuresEnabled += Features.Kapt
      if (generatedSourcesDir.resolve("source/kapt").walkEachFile().none()) {
        featuresToRemove += Features.Kapt
      }
    }
    if (viewBindingEnabled.getOrElse(false)) {
      featuresEnabled += Features.ViewBinding
      if (
        generatedSourcesDir
          .resolve("source/data_binding_base_class_source_out")
          .walkEachFile()
          .none()
      ) {
        featuresToRemove += Features.ViewBinding
      }
    }
    if (daggerCompilerEnabled.getOrElse(false)) {
      featuresEnabled += Features.DaggerCompiler
      if (!Features.DaggerCompiler.hasAnnotationsUsedIn(srcsDir)) {
        featuresToRemove += Features.DaggerCompiler
      }
      if (generatedSourcesDir.resolve("source/kapt").walkEachFile().none()) {
        featuresToRemove += Features.DaggerCompiler
      }
    }

    // TODO iterate over source files and check for matching annotations
    if (composeEnabled.getOrElse(false)) {
      featuresEnabled += Features.Compose
      if (!Features.Compose.hasAnnotationsUsedIn(srcsDir)) {
        featuresToRemove += Features.Compose
      }
    }
    if (daggerEnabled.getOrElse(false)) {
      featuresEnabled += Features.Dagger
      if (!Features.Dagger.hasAnnotationsUsedIn(srcsDir)) {
        featuresToRemove += Features.Dagger
      }
    }
    if (moshiCodeGenEnabled.getOrElse(false)) {
      featuresEnabled += Features.MoshiCodeGen
      if (!Features.MoshiCodeGen.hasAnnotationsUsedIn(srcsDir)) {
        featuresToRemove += Features.MoshiCodeGen
      }
    }
    if (circuitCodeGenEnabled.getOrElse(false)) {
      featuresEnabled += Features.CircuitInject
      if (!Features.CircuitInject.hasAnnotationsUsedIn(srcsDir)) {
        featuresToRemove += Features.CircuitInject
      }
    }

    val topography =
      ModuleTopography(
        name = projectName.get(),
        gradlePath = projectPath.get(),
        features = featuresEnabled.toSortedSet(compareBy { it.name }),
      )

    JsonWriter.of(topographyOutputFile.asFile.get().sink().buffer()).use {
      MOSHI.adapter<ModuleTopography>().toJson(it, topography)
    }
    JsonWriter.of(featuresToRemoveOutputFile.asFile.get().sink().buffer()).use {
      MOSHI.adapter<Set<ModuleFeature>>()
        .toJson(it, featuresToRemove.toSortedSet(compareBy { it.name }))
    }
  }

  @OptIn(ExperimentalPathApi::class)
  private fun ModuleFeature.hasAnnotationsUsedIn(srcsDir: Path): Boolean {
    logger.debug("Checking for $name annotation usages in sources")
    return srcsDir
      .walkEachFile()
      .run {
        if (matchingAnnotationsExtensions.isNotEmpty()) {
          filter { it.extension in Features.Compose.matchingAnnotationsExtensions }
        } else {
          this
        }
      }
      .any { file ->
        file.useLines { lines ->
          for (line in lines) {
            if (matchingAnnotations.any { it in line }) {
              return@any true
            }
          }
        }
        false
      }
  }

  internal companion object {
    fun register(
      project: Project,
      foundryExtension: FoundryExtension,
    ): TaskProvider<ModuleTopographyTask> {
      val viewBindingEnabled =
        project.provider { foundryExtension.androidHandler.featuresHandler.viewBindingEnabled() }
      val task =
        project.tasks.register<ModuleTopographyTask>("moduleTopography") {
          srcsDirProperty.setDisallowChanges(project.layout.projectDirectory.dir("src"))
          buildDirProperty.setDisallowChanges(project.layout.buildDirectory)
          moshiCodeGenEnabled.setDisallowChanges(
            foundryExtension.featuresHandler.moshiHandler.moshiCodegen
          )
          circuitCodeGenEnabled.setDisallowChanges(
            foundryExtension.featuresHandler.circuitHandler.codegen
          )
          daggerEnabled.setDisallowChanges(foundryExtension.featuresHandler.daggerHandler.enabled)
          daggerCompilerEnabled.setDisallowChanges(
            foundryExtension.featuresHandler.daggerHandler.useDaggerCompiler
          )
          composeEnabled.setDisallowChanges(foundryExtension.featuresHandler.composeHandler.enabled)
          androidTestEnabled.setDisallowChanges(
            foundryExtension.androidHandler.featuresHandler.androidTest
          )
          this.viewBindingEnabled.setDisallowChanges(viewBindingEnabled)
          topographyOutputFile.setDisallowChanges(
            project.layout.buildDirectory.file("foundry/topography/topography.json")
          )
          featuresToRemoveOutputFile.setDisallowChanges(
            project.layout.buildDirectory.file("foundry/topography/featuresToRemove.json")
          )

          // Depend on compilations
          mustRunAfter(project.tasks.withType(JavaCompile::class.java))
          mustRunAfter(project.tasks.withType(KotlinCompile::class.java))
        }

      project.pluginManager.withPlugin("dev.google.devtools.ksp") {
        task.configure { kspEnabled.set(true) }
      }
      project.pluginManager.withPlugin("org.jetbrains.kotlin.kapt") {
        task.configure { kaptEnabled.set(true) }
      }
      return task
    }
  }
}

@JsonClass(generateAdapter = true)
public data class ModuleTopography(
  val name: String,
  val gradlePath: String,
  val features: Set<ModuleFeature>,
)

@JsonClass(generateAdapter = true)
public data class ModuleFeature(
  val name: String,
  val removalMessage: String?,
  /** Generated sources root dir, if any. Note that descendants are checked */
  val generatedSourcesDir: String? = null,
  val generatedSourcesExtensions: Set<String> = emptySet(),
  val matchingAnnotations: Set<String> = emptySet(),
  val matchingAnnotationsExtensions: Set<String> = emptySet(),
  /** If specified, looks for any sources in this dir */
  val matchingSourcesDir: String? = null,
)

internal object Features {
  internal val AndroidTest =
    ModuleFeature(
      name = "androidTest",
      removalMessage = "Remove foundry.android.features.androidTest from your build file",
      matchingSourcesDir = "src/androidTest",
    )

  internal val Compose =
    ModuleFeature(
      name = "compose",
      removalMessage = "Remove foundry.features.compose from your build file",
      matchingAnnotations = setOf("@Composable"),
      matchingAnnotationsExtensions = setOf("kt"),
    )

  internal val Dagger =
    ModuleFeature(
      name = "dagger",
      removalMessage = "Remove foundry.features.dagger from your build file",
      matchingAnnotations =
        setOf(
          "@Inject",
          "@AssistedInject",
          "@ContributesTo",
          "@ContributesBinding",
          "@ContributesMultibinding",
          "@Module",
          "@Component",
          "@Subcomponent",
        ),
      matchingAnnotationsExtensions = setOf("kt", "java"),
    )

  internal val DaggerCompiler =
    ModuleFeature(
      name = "dagger-compiler",
      removalMessage = "Remove foundry.features.dagger.mergeComponents from your build file",
      matchingAnnotations = setOf("@Component", "@Subcomponent"),
      matchingAnnotationsExtensions = setOf("kt", "java"),
      generatedSourcesDir = "build/generated/source/kapt",
      generatedSourcesExtensions = setOf("java"),
    )

  internal val MoshiCodeGen =
    ModuleFeature(
      name = "moshi-codegen",
      removalMessage = "Remove foundry.features.moshi.codeGen from your build file",
      matchingAnnotations = setOf("@JsonClass"),
      matchingAnnotationsExtensions = setOf("kt"),
    )

  internal val CircuitInject =
    ModuleFeature(
      name = "circuit-inject",
      removalMessage = "Remove foundry.features.circuit.codeGen from your build file",
      matchingAnnotations = setOf("@CircuitInject"),
      matchingAnnotationsExtensions = setOf("kt"),
    )

  internal val Ksp =
    ModuleFeature(
      name = "ksp",
      removalMessage = "Remove whatever is using KSP",
      generatedSourcesDir = "build/generated/ksp",
      // Don't specify extensions because KAPT can generate anything into resources
    )

  internal val Kapt =
    ModuleFeature(
      name = "kapt",
      removalMessage = "Remove whatever is using KAPT",
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
