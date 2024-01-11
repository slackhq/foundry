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
@file:JvmName("PropertyUtil")

package slack.gradle.util

import java.util.Properties
import kotlin.contracts.contract
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

// Gradle's map {} APIs sometimes are interpreted by Kotlin to be non-null only but legally allow
// null returns. This
// abuses kotlin contracts to safe cast without a null check.
// https://github.com/gradle/gradle/issues/12388
internal fun <T> sneakyNull(value: T? = null): T {
  markAsNonNullForGradle(value)
  return value
}

// Gradle's map {} APIs sometimes are interpreted by Kotlin to be non-null only but legally allow
// null returns. This
// abuses kotlin contracts to safe cast without a null check.
internal fun <T> markAsNonNullForGradle(value: T?) {
  contract { returns() implies (value != null) }
}

/** Implementation of provider holding a start parameter's parsed project properties. */
internal abstract class StartParameterProperties :
  ValueSource<Map<String, String>, StartParameterProperties.Parameters> {
  interface Parameters : ValueSourceParameters {
    val properties: MapProperty<String, String>
  }

  override fun obtain(): Map<String, String>? {
    return parameters.properties.getOrElse(emptyMap())
  }
}

/** Implementation of provider holding a local properties file's parsed [Properties]. */
internal abstract class LocalProperties : ValueSource<Properties, LocalProperties.Parameters> {
  interface Parameters : ValueSourceParameters {
    val propertiesFile: RegularFileProperty
  }

  override fun obtain(): Properties? {
    val provider = parameters.propertiesFile
    if (!provider.isPresent) {
      return null
    }
    val propertiesFile = provider.asFile.get()
    if (!propertiesFile.exists()) {
      return null
    }
    return Properties().apply { propertiesFile.inputStream().use(::load) }
  }
}

/** Gets or creates a cached extra property. */
internal fun <T> Project.getOrCreateExtra(key: String, body: (Project) -> T): T {
  with(project.extensions.extraProperties) {
    if (!has(key)) {
      set(key, body(project))
    }
    @Suppress("UNCHECKED_CAST")
    return get(key) as? T ?: body(project) // Fallback if multiple class loaders are involved
  }
}

/**
 * Abstraction for loading a [Properties] provider that handles caching automatically per-project.
 * This way files are only ever parsed at most once per project. The goal for this is to build on
 * top of [LocalProperties] and provide a more convenient API for accessing properties from multiple
 * sources in a configuration-caching-compatible way.
 */
private fun Project.localPropertiesProvider(
  key: String,
  cacheKey: String,
  filePath: String,
): Provider<String> {
  val provider = project.getOrCreateExtra(cacheKey) { it.createPropertiesProvider(filePath) }
  return provider.map { it.getProperty(key) }
}

internal fun Project.createPropertiesProvider(filePath: String): Provider<Properties> {
  return project.providers.of(LocalProperties::class.java) {
    parameters.propertiesFile.setDisallowChanges(project.layout.projectDirectory.file(filePath))
  }
}

/** Returns a provider of a property _only_ contained in this project's local.properties. */
internal fun Project.localProperty(key: String): Provider<String> {
  return localPropertiesProvider(
    key,
    "slack.properties.provider.local-properties",
    "local.properties"
  )
}

/** Returns a provider of a property _only_ contained in this project's local gradle.properties. */
// Local gradle properties are not compatible with configuration caching and thus not accessible
// from providers.gradleProperty -_-. https://github.com/gradle/gradle/issues/13302
internal fun Project.localGradleProperty(key: String): Provider<String> {
  return localPropertiesProvider(
    key,
    "slack.properties.provider.local-gradle-properties",
    "gradle.properties"
  )
}

private fun Project.emptyStringProvider(): (String) -> Provider<String> = { provider { null } }

/**
 * A property resolver that handles multiple property sources.
 *
 * @property project The project to resolve properties for.
 * @property startParameterProperty A provider of a property _only_ contained in the project's start
 *   parameters.
 * @property globalLocalProperty A provider of a property _only_ contained in the root project's
 *   `local.properties`.
 */
public class PropertyResolver(
  private val project: Project,
  private val startParameterProperty: (String) -> Provider<String> = project.emptyStringProvider(),
  private val globalLocalProperty: (String) -> Provider<String> = project.emptyStringProvider(),
) {

  private val providers = project.providers

  /**
   * A "safe" property access mechanism that handles multiple property sources.
   *
   * This checks in the following order of priority
   * - project-local `local.properties`
   * - project-local `gradle.properties`
   * - root-project `local.properties`
   * - root-project/global `gradle.properties`
   */
  public fun safeProperty(
    key: String,
  ): Provider<String> =
    startParameterProperty(key) // start parameters
      .orElse(project.localProperty(key)) // project-local `local.properties`
      .orElse(project.localGradleProperty(key)) // project-local `gradle.properties`
      .orElse(globalLocalProperty(key)) // root-project `local.properties`
      .orElse(project.providers.gradleProperty(key)) // root-project/global `gradle.properties`

  // TODO rename these scalar types to <type>Value
  internal fun booleanProperty(key: String, defaultValue: Boolean = false): Boolean {
    return booleanProvider(key, defaultValue).get()
  }

  internal fun booleanProperty(key: String, defaultValue: Provider<Boolean>): Boolean {
    return booleanProvider(key, defaultValue).get()
  }

  internal fun booleanProvider(key: String, defaultValue: Boolean = false): Provider<Boolean> {
    return booleanProvider(key, providers.provider { defaultValue })
  }

  internal fun booleanProvider(key: String, defaultValue: Provider<Boolean>): Provider<Boolean> {
    return booleanProvider(key).orElse(defaultValue)
  }

  internal fun booleanProvider(
    key: String,
  ): Provider<Boolean> {
    return safeProperty(key).mapToBoolean()
  }

  internal fun intProperty(key: String, defaultValue: Int = -1): Int {
    return intProvider(key, defaultValue).get()
  }

  internal fun intProperty(key: String, defaultValue: Provider<Int>): Int {
    return intProvider(key, defaultValue).get()
  }

  internal fun intProvider(key: String, defaultValue: Int = -1): Provider<Int> {
    return intProvider(key, providers.provider { defaultValue })
  }

  internal fun intProvider(key: String, defaultValue: Provider<Int>): Provider<Int> {
    return safeProperty(key).mapToInt().orElse(defaultValue)
  }

  internal fun stringProperty(key: String): String {
    return optionalStringProperty(key)
      ?: error("No property for $key found and no default value was provided.")
  }

  internal fun stringProperty(key: String, defaultValue: String): String {
    return optionalStringProperty(key, defaultValue)
      ?: error("No property for $key found and no default value was provided.")
  }

  internal fun optionalStringProperty(key: String, defaultValue: String? = null): String? {
    return safeProperty(key).orNull ?: defaultValue
  }

  internal fun optionalStringProvider(key: String): Provider<String> {
    return optionalStringProvider(key, null)
  }

  internal fun optionalStringProvider(key: String, defaultValue: String? = null): Provider<String> {
    return safeProperty(key).let { defaultValue?.let { providers.provider { defaultValue } } ?: it }
  }
}

internal fun Provider<String>.mapToBoolean(): Provider<Boolean> {
  return map(String::toBoolean)
}

internal fun Provider<String>.mapToInt(): Provider<Int> {
  return map(String::toInt)
}

@Suppress("RedundantNullableReturnType")
internal fun Project.synchronousEnvProperty(env: String, default: String? = null): String? {
  return providers.environmentVariable(env).getOrElse(sneakyNull(default))
}
