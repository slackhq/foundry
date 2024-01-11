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
package slack.gradle.util

import org.gradle.api.Project
import org.gradle.api.provider.Provider

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
   * Returns a "safe" property [Provider] mechanism that handles multiple property sources in a
   * hierarchical fashion.
   *
   * This checks in the following order of priority
   * - project-local `local.properties`
   * - project-local `gradle.properties`
   * - root-project `local.properties`
   * - root-project/global `gradle.properties`
   */
  public fun providerFor(
    key: String,
  ): Provider<String> =
    startParameterProperty(key) // start parameters
      .orElse(project.localProperty(key)) // project-local `local.properties`
      .orElse(project.localGradleProperty(key)) // project-local `gradle.properties`
      .orElse(globalLocalProperty(key)) // root-project `local.properties`
      .orElse(project.providers.gradleProperty(key)) // root-project/global `gradle.properties`

  public fun booleanValue(key: String, defaultValue: Boolean = false): Boolean {
    return booleanProvider(key, defaultValue).get()
  }

  public fun booleanValue(key: String, defaultValue: Provider<Boolean>): Boolean {
    return booleanProvider(key, defaultValue).get()
  }

  public fun booleanProvider(key: String, defaultValue: Boolean = false): Provider<Boolean> {
    return booleanProvider(key, providers.provider { defaultValue })
  }

  public fun booleanProvider(key: String, defaultValue: Provider<Boolean>): Provider<Boolean> {
    return booleanProvider(key).orElse(defaultValue)
  }

  public fun booleanProvider(
    key: String,
  ): Provider<Boolean> {
    return providerFor(key).mapToBoolean()
  }

  public fun intValue(key: String, defaultValue: Int = -1): Int {
    return intProvider(key, defaultValue).get()
  }

  public fun intValue(key: String, defaultValue: Provider<Int>): Int {
    return intProvider(key, defaultValue).get()
  }

  public fun intProvider(key: String, defaultValue: Int = -1): Provider<Int> {
    return intProvider(key, providers.provider { defaultValue })
  }

  public fun intProvider(key: String, defaultValue: Provider<Int>): Provider<Int> {
    return providerFor(key).mapToInt().orElse(defaultValue)
  }

  public fun stringValue(key: String): String {
    return optionalStringValue(key)
      ?: error("No property for '$key' found and no default value was provided.")
  }

  public fun stringValue(key: String, defaultValue: String): String {
    return optionalStringValue(key, defaultValue)
      ?: error("No property for '$key' found and no default value was provided.")
  }

  public fun optionalStringValue(key: String, defaultValue: String? = null): String? {
    return providerFor(key).orNull ?: defaultValue
  }

  public fun optionalStringProvider(key: String): Provider<String> {
    return optionalStringProvider(key, null)
  }

  public fun optionalStringProvider(key: String, defaultValue: String? = null): Provider<String> {
    return providerFor(key).let { defaultValue?.let { providers.provider { defaultValue } } ?: it }
  }
}

private fun Project.emptyStringProvider(): (String) -> Provider<String> = { provider { null } }
