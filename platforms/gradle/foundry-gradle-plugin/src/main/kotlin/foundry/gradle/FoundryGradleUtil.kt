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
package foundry.gradle

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.TestAndroidComponentsExtension
import com.google.common.base.CaseFormat
import foundry.gradle.properties.gitExecProvider
import foundry.gradle.properties.mapToBoolean
import java.io.File
import java.util.Locale
import java.util.Optional
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** If true, this is currently running on GitHub Actions CI. */
public val Project.isActionsCi: Boolean
  get() = providers.environmentVariable("GITHUB_ACTIONS").mapToBoolean().getOrElse(false)

/** If true, this is currently running on Buildkite. */
public val Project.isBuildkite: Boolean
  get() = providers.environmentVariable("BUILDKITE").mapToBoolean().getOrElse(false)

/** If true, this is currently running on any CI. */
public val Project.isCi: Boolean
  get() = isActionsCi || isBuildkite

/** Returns the git branch this is running on. */
public fun Project.gitBranchProvider(): Provider<String> {
  return when {
    isBuildkite -> providers.environmentVariable("BUILDKITE_BRANCH")
    else ->
      providers.gitExecProvider("git", "rev-parse", "--abbrev-ref", "HEAD").map {
        it.lines()[0].trim()
      }
  }
}

@OptIn(ExperimentalPathApi::class)
internal fun robolectricJars(gradleUserHomeDir: File, createDirsIfMissing: Boolean = true): File {
  val foundryHome = File(gradleUserHomeDir, "foundry")

  // Migrate from existing slack
  val slackHome = File(gradleUserHomeDir, "slack")
  if (slackHome.exists()) {
    println("Migrating slack home to foundry")
    slackHome.copyRecursively(foundryHome, overwrite = true)
    slackHome.toPath().deleteRecursively()
  }

  foundryHome.apply {
    if (createDirsIfMissing) {
      if (!exists()) {
        mkdir()
      }
    }
  }

  return File(foundryHome, "robolectric-jars").apply {
    if (createDirsIfMissing) {
      if (!exists()) {
        mkdir()
      }
    }
  }
}

public fun Project.supportedLanguages(
  supportedLanguages: SupportedLanguagesEnum
): Provider<List<String>> {
  val foundryProperties = project.foundryProperties
  val gaLanguages = foundryProperties.supportedLanguages.map { it.split(",") }

  val internalLanguages = foundryProperties.supportedLanguagesInternal.map { it.split(",") }

  val betaLanguages = foundryProperties.supportedLanguagesBeta.map { it.split(",") }

  return when (supportedLanguages) {
    SupportedLanguagesEnum.GA -> gaLanguages
    SupportedLanguagesEnum.INTERNAL -> internalLanguages.zip(gaLanguages, List<String>::union)
    SupportedLanguagesEnum.BETA -> betaLanguages.zip(gaLanguages, List<String>::union)
  }.map { it.filter { it.isNotBlank() } }
}

public enum class SupportedLanguagesEnum {
  /** Languages included in the GA release */
  GA,

  /** Languages included in internal builds */
  INTERNAL,

  /** Languages included in Beta builds */
  BETA,
}

public fun Project.fullGitShaProvider(): Provider<String> {
  return providers.gitExecProvider("git", "rev-parse", "HEAD")
}

public fun Project.gitShaProvider(): Provider<String> {
  return providers.gitExecProvider("git", "rev-parse", "--short", "HEAD")
}

public val Project.ciBuildNumber: Provider<String>
  get() {
    return providers
      .environmentVariable("BUILD_NUMBER")
      .orElse(providers.environmentVariable("BUILDKITE_BUILD_NUMBER"))
  }

public val Project.jenkinsHome: Provider<String>
  get() {
    return providers.environmentVariable("JENKINS_HOME")
  }

public val Project.usePrototypeAppId: Boolean
  get() {
    return project.foundryProperties.usePrototypeAppId
  }

public fun String.decapitalizeUS(): String {
  return replaceFirstChar { it.lowercase(Locale.US) }
}

/** Capitalizes this string using [Locale.US]. */
public fun String.capitalizeUS(): String {
  return replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
}

/** Returns the variant used for `ciUnitTest` tasks on this (presumably) Android project. */
internal fun Project.ciUnitTestAndroidVariant(): String {
  val ciUnitTestVariant = project.foundryProperties.ciUnitTestVariant
  return ciUnitTestVariant.capitalizeUS()
}

internal fun Project.getVersionsCatalog(name: String = "libs"): VersionCatalog {
  return getVersionsCatalogOrNull(name) ?: error("No versions catalog found!")
}

internal fun Project.getVersionsCatalogOrNull(name: String = "libs"): VersionCatalog? {
  return try {
    project.extensions.getByType<VersionCatalogsExtension>().named(name)
  } catch (ignored: Exception) {
    null
  }
}

internal val Project.androidExtension: AndroidComponentsExtension<*, *, *>
  get() =
    androidExtensionNullable
      ?: throw IllegalArgumentException("Failed to find any registered Android extension")

internal val Project.androidExtensionNullable: AndroidComponentsExtension<*, *, *>?
  get() =
    extensions.findByType<LibraryAndroidComponentsExtension>()
      ?: extensions.findByType<ApplicationAndroidComponentsExtension>()
      ?: extensions.findByType<TestAndroidComponentsExtension>()

internal val Project.multiplatformExtension
  get() = extensions.findByType(KotlinMultiplatformExtension::class.java)

/** Returns a map of module identifiers to toml library reference aliases */
internal fun VersionCatalog.identifierMap(): Map<String, String> {
  return libraryAliases.associateBy { findLibrary(it).get().get().module.toString() }
}

/**
 * We want the following conversions:
 * - `bugsnag-gradle` -> `bugsnagGradle`
 * - `bugsnag_gradle` -> `bugsnagGradle`
 * - `bugsnag.gradle` -> `bugsnag-gradle`
 *
 * This is because `bugsnag-gradle` is converted to a nesting `bugsnag.gradle` in version accessors
 * and `bugsnag.gradle` is converted to `bugsnagGradle`. We've historically done the opposite with
 * gradle property versions though and used -/_ as separators in a continuous word and `.` for
 * nesting.
 */
internal fun tomlKey(key: String): String =
  key.replace("-", "%").replace(".", "-").replace("%", ".").replace("_", ".").snakeToCamel()

internal fun String.snakeToCamel(upper: Boolean = false): String {
  return buildString {
    var capNext = upper
    for (c in this@snakeToCamel) {
      if (c == '_' || c == '-' || c == '.') {
        capNext = true
        continue
      } else {
        if (capNext) {
          append(c.uppercaseChar())
          capNext = false
        } else {
          append(c)
        }
      }
    }
  }
}

private fun kebabCaseToCamelCase(s: String): String {
  return CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, s)
}

/**
 * Returns a project accessor representation of the given [projectPath].
 *
 * Example: `:libraries:foundation` -> `libraries.foundation`.
 */
internal fun convertProjectPathToAccessor(projectPath: String): String {
  return projectPath.removePrefix(":").split(":").joinToString(separator = ".") { segment ->
    kebabCaseToCamelCase(segment)
  }
}

/**
 * Similar to [TaskContainer.named], but waits until the task is registered if it doesn't exist,
 * yet. If the task is never registered, then this method will throw an error after the
 * configuration phase.
 */
internal inline fun <reified T : Task> Project.namedLazy(
  targetName: String,
  crossinline action: (TaskProvider<T>) -> Unit,
) {
  try {
    action(tasks.named(targetName, T::class.java))
    return
  } catch (ignored: UnknownTaskException) {}

  var didRun = false

  tasks.withType(T::class.java) {
    if (name == targetName) {
      action(tasks.named(name, T::class.java))
      didRun = true
    }
  }

  afterEvaluate {
    if (!didRun) {
      throw GradleException("Didn't find task $name with type ${T::class}.")
    }
  }
}

internal fun <T : Any> Optional<T>.asProvider(providers: ProviderFactory) =
  providers.provider { get() }
