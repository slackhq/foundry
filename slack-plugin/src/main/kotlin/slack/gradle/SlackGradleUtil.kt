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
package slack.gradle

import com.google.common.base.CaseFormat
import java.io.File
import java.util.Locale
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import slack.gradle.agp.VersionNumber
import slack.gradle.dependencies.DependencyDef
import slack.gradle.dependencies.DependencyGroup
import slack.gradle.util.gitExecProvider
import slack.gradle.util.mapToBoolean

/** If true, this is currently running on GitHub Actions CI. */
public val Project.isActionsCi: Boolean
  get() = providers.environmentVariable("GITHUB_ACTIONS").mapToBoolean().getOrElse(false)

/** If true, this is currently running on Buildkite. */
public val Project.isBuildkite: Boolean
  get() = providers.environmentVariable("BUILDKITE").mapToBoolean().getOrElse(false)

/** If true, this is currently running on any CI. */
public val Project.isCi: Boolean
  get() = isActionsCi || isBuildkite

/** Useful helper for resolving a `group:name:version` bom notation for a [DependencyGroup]. */
internal fun DependencyGroup.toBomDependencyDef(): DependencyDef {
  checkNotNull(bomArtifact) { "No bom found for group ${this::class.simpleName}" }
  return DependencyDef(group, bomArtifact, gradleProperty = groupGradleProperty)
}

/** Returns the git branch this is running on. */
public fun Project.gitBranch(): Provider<String> {
  return when {
    isBuildkite -> providers.environmentVariable("BUILDKITE_BRANCH")
    else ->
      providers.gitExecProvider("git", "rev-parse", "--abbrev-ref", "HEAD").map {
        it.lines()[0].trim()
      }
  }
}

private const val GIT_VERSION_PREFIX = "git version "

/**
 * Parses a git [VersionNumber] from a given [gitVersion], usually from a command line `git
 * --version` output.
 */
internal fun parseGitVersion(gitVersion: String?): VersionNumber {
  if (!gitVersion.isNullOrBlank()) {
    val trimmed = gitVersion.trim()
    val split = trimmed.split("\n").map { it.trim() }
    val versionLine =
      if (split.size > 1) {
        split.first { it.startsWith(GIT_VERSION_PREFIX) }
      } else {
        split[0]
      }
    val version = versionLine.removePrefix("git version ")
    return VersionNumber.parse(version)
  }

  return VersionNumber.UNKNOWN
}

internal fun robolectricJars(gradleUserHomeDir: File, createDirsIfMissing: Boolean = true): File {
  val slackHome =
    File(gradleUserHomeDir, "slack").apply {
      if (createDirsIfMissing) {
        if (!exists()) {
          mkdir()
        }
      }
    }
  return File(slackHome, "robolectric-jars").apply {
    if (createDirsIfMissing) {
      if (!exists()) {
        mkdir()
      }
    }
  }
}

public fun Project.supportedLanguages(supportedLanguages: SupportedLanguagesEnum): List<String> {
  val slackProperties = SlackProperties(project)
  val gaLanguages = slackProperties.supportedLanguages.split(",")

  val internalLanguages = slackProperties.supportedLanguagesInternal.split(",")

  val betaLanguages = slackProperties.supportedLanguagesBeta.split(",")

  return when (supportedLanguages) {
    SupportedLanguagesEnum.GA -> gaLanguages.toList().filter { it.isNotBlank() }
    SupportedLanguagesEnum.INTERNAL ->
      internalLanguages.union(gaLanguages).toList().filter { it.isNotBlank() }
    SupportedLanguagesEnum.BETA ->
      betaLanguages.union(gaLanguages).toList().filter { it.isNotBlank() }
  }
}

public enum class SupportedLanguagesEnum {
  /** Languages included in the GA release */
  GA,

  /** Languages included in internal builds */
  INTERNAL,

  /** Languages included in Beta builds */
  BETA
}

public val Project.fullGitSha: Provider<String>
  get() {
    return providers.gitExecProvider("git", "rev-parse", "HEAD")
  }

public val Project.gitSha: Provider<String>
  get() {
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
    return SlackProperties(this).usePrototypeAppId
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
  val ciUnitTestVariant = SlackProperties(this).ciUnitTestVariant
  return ciUnitTestVariant.capitalizeUS()
}

internal fun Project.jdkVersion(): Int {
  return SlackProperties(this).jdkVersion
}

internal fun Project.jvmTargetVersion(): Int {
  return SlackProperties(this).jvmTarget
}

internal fun Project.getVersionsCatalog(): VersionCatalog {
  return getVersionsCatalogOrNull() ?: error("No versions catalog found!")
}

internal fun Project.getVersionsCatalogOrNull(): VersionCatalog? {
  return try {
    project.extensions.getByType<VersionCatalogsExtension>().named("libs")
  } catch (ignored: Exception) {
    null
  }
}

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
  crossinline action: (TaskProvider<T>) -> Unit
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
