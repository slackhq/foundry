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

package slack.gradle

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.VariantOutput
import com.android.build.gradle.AppPlugin
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
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import slack.gradle.util.localGradleProperty

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

  private val VariantOutput.abiString: String?
    get() {
      return filters.find { it.filterType == FilterConfiguration.FilterType.ABI }?.identifier
    }

  @Suppress("LongMethod")
  override fun apply(project: Project) {
    project.plugins.withType<AppPlugin> {
      val versionMajor = project.localGradleProperty("versionMajor")
      val versionMinor = project.localGradleProperty("versionMinor")
      val versionPatch = project.localGradleProperty("versionPatch")

      val user =
        project
          .ciBuildNumber
          .map { "" }
          // Only provider the user if this is _not_ running on jenkins. Composition of properties
          // is still a little weird in Gradle.
          .orElse(project.providers.environmentVariable("USER"))

      val versionNameProvider =
        project.providers.provider {
          val prev = "$versionMajor.$versionMinor.$versionPatch"
          val addOn =
            user.map { it.takeIf { it.isNotEmpty() }?.let { presentUser -> "-$presentUser" } ?: "" }
          "$prev$addOn"
        }

      val ciVersionFileProvider =
        project.rootProject.layout.projectDirectory.file("ci/release.version")
      val ciContent = project.providers.fileContents(ciVersionFileProvider)
      val versionCodeProvider: Provider<Int> =
        project
          .ciBuildNumber
          .flatMap { ciContent.asText.map { it.toInt() } }
          .orElse(project.providers.provider { ApkVersioning.DEFAULT_VERSION_CODE })

      configureVariants(project, versionNameProvider, versionCodeProvider)

      // Register a version properties task. This is run on ci via android_preflight.sh
      val shortGitShaProvider = project.gitSha
      val longGitShaProvider = project.fullGitSha
      project.tasks.register<VersionPropertiesTask>("generateVersionProperties") {
        outputFile.set(
          project.layout.buildDirectory.file("intermediates/versioning/version.properties")
        )
        versionCode.set(versionCodeProvider)
        versionName.set(versionNameProvider)
        shortGitSha.set(shortGitShaProvider)
        longGitSha.set(longGitShaProvider)
        this.versionMajor.set(versionMajor)
        this.versionMinor.set(versionMinor)
      }
    }
  }

  private fun configureVariants(
    project: Project,
    versionNameProvider: Provider<String>,
    versionCodeProvider: Provider<Int>
  ) {
    // Register version calculations for variants
    project.configure<ApplicationAndroidComponentsExtension> {
      onVariants { variant ->
        val flavorName = variant.flavorName

        val mappedVersionNameProvider =
          if (flavorName != "external") {
            // Replacement for versionNameSuffix, which this overrides
            versionNameProvider.map { "$it-$flavorName" }
          } else {
            versionNameProvider
          }

        // Have to iterate outputs because of APK splits.
        variant.outputs.forEach { variantOutput ->
          variantOutput.versionName.set(mappedVersionNameProvider)

          // Reuse the same task and just remap its value as needed
          val mappedVersionCodeProvider =
            versionCodeProvider.map { rawCode ->
              @Suppress("MagicNumber")
              ApkVersioning.VERSION_CODES.getValue(variantOutput.abiString) * 10000000 + rawCode
            }
          variantOutput.versionCode.set(mappedVersionCodeProvider)
        }
      }
    }
  }
}

private object ApkVersioning {

  const val DEFAULT_VERSION_CODE: Int = 9999

  // Override version code based on the ABI
  // https://androidbycode.wordpress.com/2015/06/30/android-ndk-version-code-scheme-for-publishing-apks-per-architecture/
  val VERSION_CODES: Map<String?, Int> =
    mapOf(
      null to 0, // Universal APK for CI
      "arm64-v8a" to 3,
      "armeabi-v7a" to 2,
      "x86" to 8,
      "x86_64" to 9
    )
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
