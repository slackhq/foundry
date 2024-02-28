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
package slack.gradle.bazel

import com.android.build.gradle.internal.publishing.AndroidArtifacts
import java.util.SortedSet
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
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

/** A spec for a plain kotlin jvm project. */
internal class JvmProjectSpec(builder: Builder) {
  /**
   * The name of the project. Usually just the directory name but could be different if there are
   * multiple targets.
   */
  val name: String = builder.name
  // Deps
  val deps: List<Dep> = builder.deps
  val exportedDeps: List<Dep> = builder.exportedDeps
  val testDeps: List<Dep> = builder.testDeps + Dep.Target("${name}_lib")
  // Source globs
  val srcGlobs: List<String> = builder.srcGlobs
  val testSrcGlobs: List<String> = builder.testSrcGlobs

  override fun toString(): String {
    val compositeTestDeps = (deps + exportedDeps + testDeps).toSortedSet()
    val deps = depsString("deps", deps)
    val exportedDeps = depsString("exportedDeps", exportedDeps)
    val testDeps = depsString("deps", compositeTestDeps)

    /*
     load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library", "kt_jvm_test")

     kt_jvm_library(
         name = "ditto_lib",
         srcs = glob(["src/main/**/*.kt"]),
         visibility = ["//visibility:public"],
         deps = [
             "@maven//:androidx_annotation_annotation",
         ],
     )

     kt_jvm_test(
         name = "ditto_test",
         srcs = glob(["src/test/**/*.kt"]),
         visibility = ["//visibility:private"],
         deps = [
             ":ditto_lib",
             "@maven//:androidx_annotation_annotation",
             "@maven//:org_jetbrains_kotlinx_kotlinx_coroutines_test",
             "@maven//:org_jetbrains_kotlin_kotlin_test",
             "@maven//:junit_junit",
             "@maven//:com_google_truth_truth",
         ],
     )
    */

    return """
      load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library", "kt_jvm_test")

      kt_jvm_library(
          name = "${name}_lib",
          srcs = glob(${srcGlobs.joinToString(separator = ",", prefix = "[", postfix = "]", transform = {"\"$it\""})}),
          visibility = ["//visibility:public"],
          $deps
          $exportedDeps
      )

      kt_jvm_test(
          name = "${name}_test",
          srcs = glob(${testSrcGlobs.joinToString(separator = ",", prefix = "[", postfix = "]", transform = {"\"$it\""})}),
          visibility = ["//visibility:private"],
          $testDeps
      )
    """
      .trimIndent()
  }

  fun writeTo(path: Path, fs: FileSystem = FileSystem.SYSTEM) {
    fs.createDirectories(path)
    fs.write(path.resolve("BUILD.bazel")) { writeUtf8(toString()) }
  }

  private fun depsString(name: String, deps: Collection<Dep>): String {
    return if (deps.isNotEmpty()) {
      "$name = " +
        deps.joinToString(separator = ",\n", prefix = "[", postfix = "]") { "        \"$it\"" } +
        ","
    } else {
      ""
    }
  }

  class Builder(val name: String) {
    val deps = mutableListOf<Dep>()
    val exportedDeps = mutableListOf<Dep>()
    val testDeps = mutableListOf<Dep>()
    val srcGlobs = mutableListOf("src/main/**/*.kt", "src/main/**/*.java")
    val testSrcGlobs = mutableListOf("src/test/**/*.kt", "src/test/**/*.java")

    fun addDep(dep: Dep) {
      deps.add(dep)
    }

    fun addExportedDep(dep: Dep) {
      exportedDeps.add(dep)
    }

    fun addSrcGlob(glob: String) {
      srcGlobs.add(glob)
    }

    fun addTestSrcGlob(glob: String) {
      testSrcGlobs.add(glob)
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
    val depsIdentifiers = deps.mapDeps()
    val exportedDepsIdentifiers = exportedDeps.mapDeps()

    JvmProjectSpec.Builder(name.get())
      .apply {
        depsIdentifiers.forEach { addDep(it) }
        exportedDepsIdentifiers.forEach { addExportedDep(it) }
      }
      .build()
      .writeTo(projectDir.get().asFile.toOkioPath())
  }

  private fun SetProperty<ResolvedArtifactResult>.mapDeps(): SortedSet<Dep> {
    return map { result ->
        result
          .asSequence()
          .map { it.id }
          .map { component ->
            when (component) {
              is ModuleComponentArtifactIdentifier -> {
                val componentId = component.componentIdentifier
                val identifier = "${componentId.group}:${componentId.module}"

                // Map to lower underscore format for maven sourcing
                val target = identifier.replace(".", "_").replace(":", "_").replace("-", "_")
                Dep.Remote(source = "maven", path = "", target = target)
              }
              is ProjectComponentIdentifier -> {
                // Map to "path/to/local/dependency1" format
                Dep.Local(component.projectPath.removePrefix(":").replace(":", "/"))
              }
              else -> error("Unknown component type: $component")
            }
          }
      }
      .get()
      .toSortedSet()
  }

  protected fun resolvedDependenciesFrom(
    configuration: Configuration
  ): Provider<Set<ResolvedArtifactResult>> {
    return configuration.incoming
      .artifactView {
        attributes {
          attribute(AndroidArtifacts.ARTIFACT_TYPE, AndroidArtifacts.ArtifactType.AAR_OR_JAR.type)
        }
        lenient(true)
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
