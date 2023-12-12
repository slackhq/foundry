package slack.gradle.bazel

import okio.Path

// TODO how do plugins work?
//  - Compose
//  - Anvil
//  - KSP
//  - Kapt
internal class BazelBuildGenerator private constructor(builder: Builder) {
  val projectDir: Path = builder.projectDir
  /**
   * The name of the project. Usually just the directory name but could be different if there are
   * multiple targets.
   */
  val name: String = builder.name
  val type: ProjectType = builder.type
  val namespace: String = builder.namespace
  val deps: List<BazelDep> = builder.deps
  val exportedDeps: List<BazelDep> = builder.exportedDeps
  /** Source file directories. Usually just "src/main/kotlin" or "src/main/java". */
  val srcDirs: List<String> = builder.srcDirs
  /** Resources file directories. Usually just "src/main/res". */
  val resDirs: List<String> = builder.resDirs
  /**
   * The path to the manifest file. Usually just "src/main/AndroidManifest.xml".
   *
   * TODO is this required? Optional in Gradle
   */
  val manifestFile: String? = builder.manifestFile

  fun generate() {
    // load("@io_bazel_rules_kotlin//kotlin:rules.bzl", "kt_android_library")
    //
    // kt_android_library(
    //    name = "my_android_library",
    //    srcs = glob(["src/main/kotlin/**/*.kt"]),
    //    resource_files = glob(["src/main/res/**"]),
    //    manifest = "src/main/AndroidManifest.xml",
    //    custom_package = "com.example.mypackage",
    //    deps = [
    //        # Remote dependencies
    //        "@jetpack_compose//path:target", # An example remote dependency for Jetpack Compose
    //        "@kotlin_stdlib//path:target",   # An example remote dependency for Kotlin standard
    // library
    //
    //        # Local dependencies
    //        "//path/to/local/dependency1",   # An example local dependency within the project
    //        "//path/to/local/dependency2",   # Another example of a local dependency
    //
    //        # Add other dependencies as needed
    //    ],
    //    plugins = [
    //        "@compose_compiler_plugin//path:target", # An example remote plugin dependency
    //    ],
    // )
  }

  class Builder(
    val projectDir: Path,
    val name: String,
    val type: ProjectType,
    val namespace: String,
  ) {
    val deps = mutableListOf<BazelDep>()
    val exportedDeps = mutableListOf<BazelDep>()
    val srcDirs = mutableListOf<String>()
    val resDirs = mutableListOf<String>()
    var manifestFile: String? = null

    // TODO
    //  - Android features
    //  - Android/Kotlin builder?
    //  - Kotlin features

    fun build(): BazelBuildGenerator = BazelBuildGenerator(this)
  }
}

internal enum class ProjectType {
  KOTLIN_JVM,
  KOTLIN_ANDROID,
  JAVA,
  JAVA_ANDROID
}

/**
 * A Bazel dependency, either `@jetpack_compose//path:target` (remote) or
 * `//path/to/local/dependency1` (local).
 */
internal data class BazelDep(val path: String)
