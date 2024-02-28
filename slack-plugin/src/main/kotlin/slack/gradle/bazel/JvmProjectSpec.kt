package slack.gradle.bazel

import com.android.build.gradle.internal.publishing.AndroidArtifacts
import okio.FileSystem
import okio.Path
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import slack.gradle.register

// TODO what about java files? What does the kt_jvm_library rule do?
/** A spec for a plain kotlin jvm project. */
internal class JvmProjectSpec(builder: Builder) {
  /**
   * The name of the project. Usually just the directory name but could be different if there are
   * multiple targets.
   */
  val name: String = builder.name
  val deps: List<Dep> = builder.deps
  val exportedDeps: List<Dep> = builder.exportedDeps
  /** Source globs. */
  val srcGlobs: List<String> = builder.srcGlobs

  override fun toString(): String {
    val deps =
      if (deps.isNotEmpty()) {
        "deps = " +
          deps.joinToString(separator = ",\n", prefix = "[", postfix = "]") { "        \"$it\"" } +
          ","
      } else {
        ""
      }
    val exportedDeps =
      if (exportedDeps.isNotEmpty()) {
        "exportedDeps = " +
          exportedDeps.joinToString(separator = ",\n", prefix = "[", postfix = "]") {
            "        \"$it\""
          } +
          ","
      } else {
        ""
      }

    /*
     load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

     kt_jvm_library(
         name = "ditto",
         srcs = glob(["src/**/*.kt"]),
         deps = [
             "@maven//:androidx_annotation_annotation",
             "@maven//:org_jetbrains_kotlinx_kotlinx_coroutines_test",
             "@maven//:org_jetbrains_kotlin_kotlin_test",
             "@maven//:junit_junit",
             "@maven//:com_google_truth_truth",
         ],
     )
    */

    return """
      load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

      kt_jvm_library(
          name = "$name",
          srcs = glob(${srcGlobs.joinToString(separator = ",", prefix = "[", postfix = "]", transform = {"\"$it\""})}),
          visibility = ["//visibility:public"],
          $deps
          $exportedDeps
      )
    """
      .trimIndent()
  }

  fun writeTo(path: Path, fs: FileSystem = FileSystem.SYSTEM) {
    fs.createDirectories(path)
    fs.write(path.resolve("BUILD.bazel")) { writeUtf8(toString()) }
  }

  class Builder(val name: String) {
    val deps = mutableListOf<Dep>()
    val exportedDeps = mutableListOf<Dep>()
    val srcGlobs = mutableListOf("src/**/*.kt")

    fun addDep(dep: Dep) {
      deps.add(dep)
    }

    fun addExportedDep(dep: Dep) {
      exportedDeps.add(dep)
    }

    fun addSrcGlob(glob: String) {
      srcGlobs.add(glob)
    }

    fun build(): JvmProjectSpec = JvmProjectSpec(this)
  }
}

@UntrackedTask(because = "Generates a Bazel BUILD file for a Kotlin JVM project")
internal abstract class JvmProjectBazelTask : DefaultTask() {
  @get:Input abstract val name: Property<String>

  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val projectDir: DirectoryProperty

  @get:Input abstract val deps: SetProperty<ResolvedArtifactResult>
  @get:Input abstract val exportedDeps: SetProperty<ResolvedArtifactResult>

  init {
    group = "bazel"
    description = "Generates a Bazel BUILD file for a Kotlin JVM project"
  }

  @TaskAction
  fun generate() {
    // TODO map identifiers to lower_underscore case and convert to bazel deps
    val depsIdentifiers = deps.mapToIdentifiers().get()
    val exportedDepsIdentifiers = exportedDeps.mapToIdentifiers().get()
  }

  // TODO just return converted Bazel deps instead?
  private fun SetProperty<ResolvedArtifactResult>.mapToIdentifiers():
    Provider<Map<String, String>> {
    return map { result ->
      result
        .asSequence()
        .map { it.id }
        .filterIsInstance<ModuleComponentArtifactIdentifier>()
        .associate { component ->
          val componentId = component.componentIdentifier
          val identifier = "${componentId.group}:${componentId.module}"
          identifier to componentId.version
        }
    }
  }

  // TODO need to update this to include project deps too
  protected fun resolvedDependenciesFrom(
    configuration: Configuration
  ): Provider<Set<ResolvedArtifactResult>> {
    return configuration.incoming
      .artifactView {
        attributes {
          attribute(AndroidArtifacts.ARTIFACT_TYPE, AndroidArtifacts.ArtifactType.AAR_OR_JAR.type)
        }
        lenient(true)
        // Only resolve external dependencies! Without this, all project dependencies will get
        // _compiled_.
        componentFilter { id -> id is ModuleComponentIdentifier }
      }
      .artifacts
      .resolvedArtifacts
  }

  companion object {
    fun register(
      project: Project,
      depsConfiguration: Configuration,
      exportedDepsConfiguration: Configuration,
    ) {
      project.tasks.register<JvmProjectBazelTask>("generateBazel") {
        name.set(project.name)
        projectDir.set(project.layout.projectDirectory)
        deps.set(resolvedDependenciesFrom(depsConfiguration))
        exportedDeps.set(resolvedDependenciesFrom(exportedDepsConfiguration))
      }
    }
  }
}
