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
@file:Suppress("UnstableApiUsage")

package foundry.gradle

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.gradle.AppPlugin
import foundry.gradle.properties.localGradleProperty
import foundry.gradle.properties.setDisallowChanges
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Uses AGP 4.0's new APIs for manipulating variant properties in a more cache-friendly way.
 *
 * Normally we should use cacheable tasks and reuse their values, but manifest manipulation appears
 * to break their task dependencies in a way we don't understand. Since our computations are cheap,
 * we just compute them on the fly for now.
 *
 * Based on examples here: https://github.com/android/gradle-recipes
 */
@Suppress("unused")
internal class ApkVersioningPlugin : Plugin<Project> {

  @Suppress("LongMethod")
  override fun apply(project: Project) {
    project.plugins.withType(AppPlugin::class.java) {
      val versionMajor = project.localGradleProperty("versionMajor")
      val versionMinor = project.localGradleProperty("versionMinor")
      val versionPatch = project.localGradleProperty("versionPatch")

      val foundryProperties = FoundryProperties(project)

      val debugVersionNameProvider =
        versionNameProvider(
          versionMajor,
          versionMinor,
          versionPatch,
          user = project.provider { foundryProperties.debugUserString },
        )

      val defaultVersionNameProvider =
        versionNameProvider(
          versionMajor,
          versionMinor,
          versionPatch,
          user =
            project.ciBuildNumber
              .map { "" }
              // Only provider the user if this is _not_ running on CI. Composition of properties
              // is still a little weird in Gradle.
              .orElse(project.providers.environmentVariable("USER")),
        )

      val debugVersionCodeProvider: Provider<Int> =
        project.provider { FoundryProperties(project).debugVersionCode }

      val ciVersionFileProvider =
        project.rootProject.layout.projectDirectory.file("ci/release.version")
      val ciContent = project.providers.fileContents(ciVersionFileProvider)
      val defaultVersionCodeProvider: Provider<Int> =
        project.ciBuildNumber
          .flatMap { ciContent.asText.map { it.toInt() } }
          .orElse(debugVersionCodeProvider)

      configureVariants(
        project,
        debugVersionNameProvider,
        debugVersionCodeProvider,
        defaultVersionNameProvider,
        defaultVersionCodeProvider,
      )

      // Register a version properties task. This is run on ci via android_preflight.sh
      val shortGitShaProvider = project.gitSha
      val longGitShaProvider = project.fullGitSha
      project.tasks.register<VersionPropertiesTask>("generateVersionProperties") {
        outputFile.setDisallowChanges(
          project.layout.buildDirectory.file("intermediates/versioning/version.properties")
        )
        versionCode.setDisallowChanges(defaultVersionCodeProvider)
        versionName.setDisallowChanges(defaultVersionNameProvider)
        shortGitSha.setDisallowChanges(shortGitShaProvider)
        longGitSha.setDisallowChanges(longGitShaProvider)
        this.versionMajor.setDisallowChanges(versionMajor)
        this.versionMinor.setDisallowChanges(versionMinor)
      }
    }
  }

  private fun configureVariants(
    project: Project,
    debugVersionNameProvider: Provider<String>,
    debugVersionCodeProvider: Provider<Int>,
    defaultVersionNameProvider: Provider<String>,
    defaultVersionCodeProvider: Provider<Int>,
  ) {
    // Register version calculations for variants
    project.configure<ApplicationAndroidComponentsExtension> {
      onVariants { variant ->
        val flavorName = variant.flavorName

        val versionCodeProvider =
          if (variant.buildType == "debug") {
            debugVersionCodeProvider
          } else {
            defaultVersionCodeProvider
          }
        val versionNameProvider =
          if (variant.buildType == "debug") {
            debugVersionNameProvider
          } else {
            defaultVersionNameProvider
          }
        val mappedVersionNameProvider =
          if (flavorName != "external") {
            // Replacement for versionNameSuffix, which this overrides
            versionNameProvider.map { "$it-$flavorName" }
          } else {
            versionNameProvider
          }

        // Have to iterate outputs because of APK splits.
        variant.outputs.forEach { variantOutput ->
          variantOutput.versionName.setDisallowChanges(mappedVersionNameProvider)
          variantOutput.versionCode.setDisallowChanges(versionCodeProvider)
        }
      }
    }
  }

  private fun versionNameProvider(
    versionMajor: Provider<String>,
    versionMinor: Provider<String>,
    versionPatch: Provider<String>,
    user: Provider<String>,
  ): Provider<String> {
    return versionMajor
      .zip(versionMinor) { major, minor -> "$major.$minor" }
      .zip(versionPatch) { prev, patch -> "$prev.$patch" }
      .zip(user) { prev, possibleUser ->
        val addOn =
          possibleUser.takeIf { it.isNotEmpty() }?.let { presentUser -> "-$presentUser" } ?: ""
        "$prev$addOn"
      }
  }
}

/**
 * This generates a `version.properties` file that CI uses (see
 * `ci/master/master.multibranch.jenkinsfile`).
 */
@CacheableTask
internal abstract class VersionPropertiesTask : DefaultTask() {

  @get:Input abstract val versionMajor: Property<String>

  @get:Input abstract val versionMinor: Property<String>

  @get:Input abstract val shortGitSha: Property<String>

  @get:Input abstract val longGitSha: Property<String>

  @get:Input abstract val versionName: Property<String>

  @get:Input abstract val versionCode: Property<Int>

  @get:OutputFile abstract val outputFile: RegularFileProperty

  @TaskAction
  fun generate() {
    val releaseVersion = "${versionMajor.get()}.${versionMinor.get()}"
    val resolvedVersionCode = versionCode.get()
    val resolvedVersionName = versionName.get()
    val versionProps =
      Properties().apply {
        setProperty("tag", "$resolvedVersionName-$resolvedVersionCode")
        setProperty("git_short", shortGitSha.get())
        setProperty("version_code", "" + resolvedVersionCode)
        setProperty("full_git_sha", longGitSha.get())
        setProperty("release_version", releaseVersion)
      }

    outputFile.asFile.get().writer().use {
      versionProps.store(it, "Auto-generated by build. Do not check in")
    }
  }
}
