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

private fun Project.emptyStringProvider(): (String) -> Provider<String> = { provider { null } }
