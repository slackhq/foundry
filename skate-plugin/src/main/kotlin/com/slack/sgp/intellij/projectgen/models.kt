package slack.tooling.projectgen

import com.squareup.kotlinpoet.FileSpec
import java.io.File

internal data class Project(
  // Gradle path
  val path: String,
  val buildFile: BuildFile,
  val readMeFile: ReadMeFile,
  val features: List<Feature>,
) {
  fun writeTo(rootDir: File) {
    val projectDir =
      rootDir.resolve(path.removePrefix(":").replace(":", "/")).apply {
        check(!exists()) { "Project already exists at $this" }
        mkdirs()
      }
    buildFile.buildFileSpec(features).writeTo(projectDir)

    readMeFile.writeTo(projectDir)

    for (feature in features) {
      feature.renderFiles(projectDir)
    }

    val settingsFile = File(rootDir, "settings-all.gradle.kts")
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
        addStatement("alias(libs.plugins.slack.base)")
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

        // slack features
        val slackFeatures = features.filterIsInstance<SlackFeatureVisitor>()
        val slackAndroidFeatures = features.filterIsInstance<SlackAndroidFeatureVisitor>()
        if (slackFeatures.isNotEmpty() || slackAndroidFeatures.isNotEmpty()) {
          addStatement("")
          beginControlFlow("slack")
          if (slackFeatures.isNotEmpty()) {
            beginControlFlow("features")
            for (feature in slackFeatures) {
              feature.writeToSlackFeatures(this)
            }
            endControlFlow()
          }
          if (slackAndroidFeatures.isNotEmpty()) {
            beginControlFlow("android")
            beginControlFlow("features")
            slackAndroidFeatures.forEach { it.writeToSlackAndroidFeatures(this) }
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
  fun renderFiles(projectDir: File) {}
}

internal interface PluginVisitor {
  // Callback within plugins { } block
  fun writeToPlugins(builder: FileSpec.Builder)
}

internal interface SlackFeatureVisitor {
  // Callback within slack.features { } block
  fun writeToSlackFeatures(builder: FileSpec.Builder)
}

internal interface SlackAndroidFeatureVisitor {
  // Callback within slack.android.features { } block
  fun writeToSlackAndroidFeatures(builder: FileSpec.Builder)
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
  val resourcesPrefix: String?,
  val viewBindingEnabled: Boolean,
  val androidTest: Boolean,
  val packageName: String,
) : Feature, PluginVisitor, AndroidBuildFeatureVisitor, SlackAndroidFeatureVisitor {
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

  override fun writeToSlackAndroidFeatures(builder: FileSpec.Builder) {
    if (androidTest) {
      builder.addStatement("androidTest()")
    }
    resourcesPrefix?.let { builder.addStatement("resources(\"$it\")") }
  }

  override fun renderFiles(projectDir: File) {
    if (androidTest) {
      val androidTestDir = File(projectDir, "src/androidTest")
      androidTestDir.mkdirs()

      // Write the manifest file
      File(androidTestDir, "AndroidManifest.xml")
        // language=XML
        .writeText(
          """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android">
            <!-- Necessary for debugging to work since our libraries are single-variant. -->
            <application android:debuggable="true" />
        </manifest>
        """
            .trimIndent()
        )

      // Write the placeholder test file
      writePlaceholderFileTo(androidTestDir, packageName)
    }
  }
}

internal data class KotlinFeature(val packageName: String, val isAndroid: Boolean) :
  Feature, PluginVisitor {
  override fun writeToPlugins(builder: FileSpec.Builder) {
    val marker = if (isAndroid) "android" else "jvm"
    builder.addStatement("alias(libs.plugins.kotlin.$marker)")
  }

  override fun renderFiles(projectDir: File) {
    writePlaceholderFileTo(projectDir.resolve("src/main"), packageName)
  }
}

private fun writePlaceholderFileTo(sourceSetDir: File, packageName: String) {
  val mainSrcDir =
    sourceSetDir.resolve("kotlin/${packageName.replace(".", "/")}").apply { mkdirs() }
  File(mainSrcDir, "Placeholder.kt")
    .writeText(
      """
      package $packageName

      /** This file exists just to create your new project's directories. Rename or delete this! */
      private abstract class Placeholder
      """
        .trimIndent()
    )
}

internal data class DaggerFeature(val runtimeOnly: Boolean) : Feature, SlackFeatureVisitor {
  override fun writeToSlackFeatures(builder: FileSpec.Builder) {
    // All these args are false by default, so only add arguments for enabled ones!
    val args =
      mapOf("runtimeOnly" to runtimeOnly)
        .filterValues { it }
        .entries
        .joinToString(",·") { (k, v) -> "$k·=·$v" }
    builder.addStatement("dagger($args)")
  }
}

internal object RobolectricFeature : Feature, SlackAndroidFeatureVisitor {
  override fun writeToSlackAndroidFeatures(builder: FileSpec.Builder) {
    builder.addStatement("robolectric()")
  }
}

internal object ComposeFeature : Feature, SlackFeatureVisitor {
  override fun writeToSlackFeatures(builder: FileSpec.Builder) {
    builder.addStatement("compose()")
  }
}

internal class ReadMeFile {
  fun writeTo(projectDir: File) {
    val projectName = projectDir.name
    File(projectDir, "README.md")
      .apply { createNewFile() }
      .bufferedWriter()
      .use { writer ->
        val underline = "=".repeat(projectName.length)
        writer.write(projectName)
        writer.appendLine()
        writer.write(underline)
      }
  }
}
