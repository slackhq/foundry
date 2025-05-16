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
package foundry.intellij.compose.projectgen

import com.squareup.kotlinpoet.FileSpec
import java.nio.file.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.writeText

internal data class Project(
  // Gradle path
  val path: String,
  val buildFile: BuildFile,
  val readMeFile: ReadMeFile,
  val features: List<Feature>,
) {

  fun checkValidPath(rootDir: Path): Boolean {
    val projectDir = rootDir.resolve(path.removePrefix(":").replace(":", "/"))
    return !projectDir.exists()
  }

  fun writeTo(rootDir: Path) {
    val projectDir =
      rootDir.resolve(path.removePrefix(":").replace(":", "/")).apply { createDirectories() }
    buildFile.buildFileSpec(features).writeTo(projectDir)

    readMeFile.writeTo(projectDir)

    for (feature in features) {
      feature.renderFiles(projectDir)
    }

    val settingsFile = rootDir.resolve("settings-all.gradle.kts")
    val includedProjects =
      settingsFile
        .readLines()
        .asSequence()
        .filter { it.startsWith("  \":") }
        .map { it.trim().removeSuffix(",").removeSurrounding("\"") }
        .plus(path)
        .sorted()

    settingsFile.writeText(
      includedProjects.joinToString(
        "\n",
        prefix = "// Please keep these in alphabetical order!\ninclude(\n",
        postfix = "\n)\n",
      ) {
        "  \"$it\","
      }
    )
  }
}

internal data class BuildFile(val dependencies: List<Dependency>) {
  fun buildFileSpec(features: List<Feature>): FileSpec {
    val fileSpecBuilder =
      FileSpec.scriptBuilder("build.gradle").apply {
        // Plugins block
        beginControlFlow("plugins")
        addStatement("alias(libs.plugins.foundry.base)")
        // TODO do we ever need to ensure no dupes?
        features.filterIsInstance<PluginVisitor>().forEach { it.writeToPlugins(this) }
        endControlFlow()

        // android build features
        val enabledAndroidBuildFeatures =
          features.filterIsInstance<AndroidBuildFeatureVisitor>().filter {
            it.hasAndroidBuildFeaturesToEnable
          }
        if (enabledAndroidBuildFeatures.isNotEmpty()) {
          addStatement("")
          beginControlFlow("android")
          beginControlFlow("buildFeatures")
          enabledAndroidBuildFeatures.forEach { it.writeToAndroidBuildFeatures(this) }
          endControlFlow()
          endControlFlow()
        }

        // foundry features
        val foundryFeatures = features.filterIsInstance<FoundryFeatureVisitor>()
        val foundryAndroidFeatures = features.filterIsInstance<FoundryAndroidFeatureVisitor>()
        if (foundryFeatures.isNotEmpty() || foundryAndroidFeatures.isNotEmpty()) {
          addStatement("")
          beginControlFlow("foundry")
          if (foundryFeatures.isNotEmpty()) {
            beginControlFlow("features")
            for (feature in foundryFeatures) {
              feature.writeToFoundryFeatures(this)
            }
            endControlFlow()
          }
          if (foundryAndroidFeatures.isNotEmpty()) {
            beginControlFlow("android")
            beginControlFlow("features")
            foundryAndroidFeatures.forEach { it.writeToFoundryAndroidFeatures(this) }
            endControlFlow()
            endControlFlow()
          }
          endControlFlow()
        }

        // dependencies
        addStatement("")
        beginControlFlow("dependencies")
        for (dep in dependencies + DEFAULT_DEPENDENCIES) {
          dep.writeTo(this)
        }
        for (dep in features.filterIsInstance<DependenciesVisitor>()) {
          dep.writeToDependencies(this)
        }
        endControlFlow()
      }
    return fileSpecBuilder.build()
  }

  companion object {
    val DEFAULT_DEPENDENCIES =
      listOf(
        Dependency("implementation", "libs.androidx.annotation"),
        Dependency("testImplementation", "libs.testing.junit"),
        Dependency("testImplementation", "libs.testing.truth"),
      )
  }
}

internal data class Dependency(
  val configuration: String,
  // Either libs.* or ":emoji" path format
  val path: String,
) {
  private val isLocal: Boolean = !path.startsWith("libs.")

  fun writeTo(builder: FileSpec.Builder) {
    val finalPath =
      if (isLocal) {
        "project(\"$path\")"
      } else {
        path
      }
    builder.addStatement("$configuration($finalPath)")
  }
}

internal interface Feature {
  fun renderFiles(projectDir: Path) {}
}

internal interface PluginVisitor {
  // Callback within plugins { } block
  fun writeToPlugins(builder: FileSpec.Builder)
}

internal interface FoundryFeatureVisitor {
  // Callback within foundry.features { } block
  fun writeToFoundryFeatures(builder: FileSpec.Builder)
}

internal interface FoundryAndroidFeatureVisitor {
  // Callback within foundry.android.features { } block
  fun writeToFoundryAndroidFeatures(builder: FileSpec.Builder)
}

internal interface AndroidBuildFeatureVisitor {
  val hasAndroidBuildFeaturesToEnable: Boolean
    get() = true

  // Callback within android.buildFeatures { } block
  fun writeToAndroidBuildFeatures(builder: FileSpec.Builder)
}

internal interface DependenciesVisitor {
  // Callback within dependencies { } block
  fun writeToDependencies(builder: FileSpec.Builder)
}

internal data class AndroidLibraryFeature(
  val resourcesPrefix: CharSequence?,
  val viewBindingEnabled: Boolean,
  val androidTest: Boolean,
  val packageName: String,
) : Feature, PluginVisitor, AndroidBuildFeatureVisitor, FoundryAndroidFeatureVisitor {
  override fun writeToPlugins(builder: FileSpec.Builder) {
    builder.addStatement("alias(libs.plugins.android.library)")
  }

  override val hasAndroidBuildFeaturesToEnable: Boolean
    get() = resourcesPrefix != null || viewBindingEnabled

  override fun writeToAndroidBuildFeatures(builder: FileSpec.Builder) {
    builder.apply {
      if (viewBindingEnabled) {
        addStatement("viewBinding = true")
      }
    }
  }

  override fun writeToFoundryAndroidFeatures(builder: FileSpec.Builder) {
    if (androidTest) {
      builder.addStatement("androidTest()")
    }
    resourcesPrefix?.let { builder.addStatement("resources(\"$it\")") }
  }
}

internal data class KotlinFeature(val packageName: String, val isAndroid: Boolean) :
  Feature, PluginVisitor {
  override fun writeToPlugins(builder: FileSpec.Builder) {
    val marker = if (isAndroid) "android" else "jvm"
    builder.addStatement("alias(libs.plugins.kotlin.$marker)")
  }

  override fun renderFiles(projectDir: Path) {
    writePlaceholderFileTo(projectDir.resolve("src/main"), packageName)
  }
}

private fun writePlaceholderFileTo(sourceSetDir: Path, packageName: String) {
  val mainSrcDir =
    sourceSetDir.resolve("kotlin/${packageName.replace(".", "/")}").apply { createDirectories() }
  mainSrcDir
    .resolve("Placeholder.kt")
    .writeText(
      """
      package $packageName

      /** This file exists just to create your new project's directories. Rename or delete this! */
      private abstract class Placeholder
      """
        .trimIndent()
    )
}

internal data class DaggerFeature(val runtimeOnly: Boolean) : Feature, FoundryFeatureVisitor {
  override fun writeToFoundryFeatures(builder: FileSpec.Builder) {
    // All these args are false by default, so only add arguments for enabled ones!
    val args =
      mapOf("runtimeOnly" to runtimeOnly)
        .filterValues { it }
        .entries
        .joinToString(",·") { (k, v) -> "$k·=·$v" }
    builder.addStatement("dagger($args)")
  }
}

internal object RobolectricFeature : Feature, FoundryAndroidFeatureVisitor {
  override fun writeToFoundryAndroidFeatures(builder: FileSpec.Builder) {
    builder.addStatement("robolectric()")
  }
}

internal object ComposeFeature : Feature, FoundryFeatureVisitor {
  override fun writeToFoundryFeatures(builder: FileSpec.Builder) {
    builder.addStatement("compose()")
  }
}

internal object CircuitFeature : Feature, FoundryFeatureVisitor {
  override fun writeToFoundryFeatures(builder: FileSpec.Builder) {
    builder.addStatement("circuit()")
  }
}

internal class ReadMeFile {
  fun writeTo(projectDir: Path) {
    val projectName = projectDir.name
    projectDir
      .resolve("README.md")
      .apply { createFile() }
      .bufferedWriter()
      .use { writer ->
        val underline = "=".repeat(projectName.length)
        writer.write(projectName)
        writer.appendLine()
        writer.write(underline)
      }
  }
}
