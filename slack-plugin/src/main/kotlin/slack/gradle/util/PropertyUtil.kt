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
import org.gradle.StartParameter
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.provider.ValueSourceSpec

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
 * Abstraction for loading a [Map] provider that handles caching automatically per root project.
 * This way properties are only ever parsed at most once per root project. The goal for this is to
 * build on top of [LocalProperties] and provide a more convenient API for accessing properties from
 * multiple sources in a configuration-caching-compatible way. Start parameters are special because
 * they come from [StartParameter.projectProperties] and are intended to supersede any other
 * property values.
 */
private fun Project.startParameterProperties(key: String): Provider<String> {
  val provider =
    project.rootProject.getOrCreateExtra("slack.properties.provider.start-properties") {
      val startParameters = project.gradle.startParameter.projectProperties
      it.providers.of(StartParameterProperties::class.java) {
        parameters.properties.set(startParameters)
      }
    }
  return provider.map { sneakyNull(it[key]) }
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
  valueSourceSpec: ValueSourceSpec<LocalProperties.Parameters>.() -> Unit
): Provider<String> {
  val provider =
    project.getOrCreateExtra(cacheKey) {
      it.providers.of(LocalProperties::class.java, valueSourceSpec)
    }
  return provider.map { it.getProperty(key) }
}

/** Returns a provider of a property _only_ contained in this project's local.properties. */
internal fun Project.localProperty(key: String): Provider<String> {
  return localPropertiesProvider(key, "slack.properties.provider.local-properties") {
    parameters.propertiesFile.set(project.layout.projectDirectory.file("local.properties"))
  }
}

/** Returns a provider of a property _only_ contained in this project's local gradle.properties. */
// Local gradle properties are not compatible with configuration caching and thus not accessible
// from
// providers.gradleProperty -_-. https://github.com/gradle/gradle/issues/13302
internal fun Project.localGradleProperty(key: String): Provider<String> {
  return localPropertiesProvider(key, "slack.properties.provider.local-gradle-properties") {
    parameters.propertiesFile.set(project.layout.projectDirectory.file("gradle.properties"))
  }
}

/**
 * A "safe" property access mechanism that handles multiple property sources.
 *
 * This checks in the following order of priority
 * - project-local `local.properties`
 * - project-local `gradle.properties`
 * - root-project `local.properties`
 * - root-project/global `gradle.properties`
 */
public fun Project.safeProperty(key: String): Provider<String> {
  return rootProject
    .startParameterProperties(key) // start parameters
    .orElse(localProperty(key)) // project-local `local.properties`
    .orElse(localGradleProperty(key)) // project-local `gradle.properties`
    .orElse(rootProject.localProperty(key)) // root-project `local.properties`
    .orElse(providers.gradleProperty(key)) // root-project/global `gradle.properties`
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

// TODO rename these scalar types to <type>Value
internal fun Project.booleanProperty(key: String, defaultValue: Boolean = false): Boolean {
  return booleanProvider(key, defaultValue).get()
}

internal fun Project.booleanProperty(key: String, defaultValue: Provider<Boolean>): Boolean {
  return booleanProvider(key, defaultValue).get()
}

internal fun Project.booleanProvider(
  key: String,
  defaultValue: Boolean = false
): Provider<Boolean> {
  return booleanProvider(key, provider { defaultValue })
}

internal fun Project.booleanProvider(
  key: String,
  defaultValue: Provider<Boolean>
): Provider<Boolean> {
  return safeProperty(key).mapToBoolean().orElse(defaultValue)
}

internal fun Project.intProperty(key: String, defaultValue: Int = -1): Int {
  return intProvider(key, defaultValue).get()
}

internal fun Project.intProperty(key: String, defaultValue: Provider<Int>): Int {
  return intProvider(key, defaultValue).get()
}

internal fun Project.intProvider(key: String, defaultValue: Int = -1): Provider<Int> {
  return intProvider(key, provider { defaultValue })
}

internal fun Project.intProvider(key: String, defaultValue: Provider<Int>): Provider<Int> {
  return safeProperty(key).mapToInt().orElse(defaultValue)
}

internal fun Project.stringProperty(key: String): String {
  return optionalStringProperty(key)
    ?: error("No property for $key found and no default value was provided.")
}

internal fun Project.stringProperty(key: String, defaultValue: String): String {
  return optionalStringProperty(key, defaultValue)
    ?: error("No property for $key found and no default value was provided.")
}

internal fun Project.optionalStringProperty(key: String, defaultValue: String? = null): String? {
  return safeProperty(key).orNull ?: defaultValue
}
