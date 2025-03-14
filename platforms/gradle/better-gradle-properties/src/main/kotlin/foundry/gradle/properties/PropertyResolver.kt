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
package foundry.gradle.properties

import java.util.Optional
import java.util.concurrent.Callable
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

/**
 * A property resolver that handles multiple property sources in a hierarchical fashion for
 * [providerFor].
 *
 * This checks in the following order of priority
 * - project-local `local.properties`
 * - project-local `gradle.properties`
 * - root-project `local.properties`
 * - root-project/global `gradle.properties`
 *
 * @property startParameterProperty A provider of a property _only_ contained in the project's start
 *   parameters.
 * @property globalLocalProperty A provider of a property _only_ contained in the root project's
 *   `local.properties`.
 */
public class PropertyResolver(
  private val providers: SimpleProviderFactory,
  private val localPropertyProvider: (String) -> Provider<String>,
  private val localGradlePropertyProvider: (String) -> Provider<String>,
  private val startParameterProperty: (String) -> Provider<String>,
  private val globalLocalProperty: (String) -> Provider<String>,
  private val globalGradleLocalProperty: (String) -> Provider<String>,
) {

  public interface SimpleProviderFactory {
    public fun <T : Any> provider(callable: Callable<T>): Provider<T>
  }

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
  public fun providerFor(key: String): Provider<String> =
    startParameterProperty(key) // start parameters
      .orElse(localPropertyProvider(key)) // project-local `local.properties`
      .orElse(localGradlePropertyProvider(key)) // project-local `gradle.properties`
      .orElse(globalLocalProperty(key)) // root-project `local.properties`
      .orElse(globalGradleLocalProperty(key)) // root-project/global `gradle.properties`

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

  public fun booleanProvider(key: String): Provider<Boolean> {
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

  public fun requiredStringProvider(key: String): Provider<String> {
    return optionalStringProvider(key)
      .orElse(
        providers.provider {
          error("No property for '$key' found and no default value was provided.")
        }
      )
  }

  public fun optionalStringValue(key: String, defaultValue: String? = null): String? {
    return providerFor(key).orNull ?: defaultValue
  }

  public fun optionalStringProvider(
    key: String,
    blankBehavior: BlankBehavior = BlankBehavior.ERROR,
  ): Provider<String> {
    return optionalStringProvider(key, null, blankBehavior)
  }

  public fun optionalStringProvider(
    key: String,
    defaultValue: String? = null,
    blankBehavior: BlankBehavior = BlankBehavior.ERROR,
  ): Provider<String> {
    return providerFor(key)
      .let { provider ->
        defaultValue?.let { provider.orElse(providers.provider { defaultValue }) } ?: provider
      }
      .filter {
        when (blankBehavior) {
          BlankBehavior.FILTER -> {
            it.isNotBlank()
          }
          BlankBehavior.ERROR -> {
            it.ifBlank { error("Unexpected blank value for property '$key'") }
            true
          }
          BlankBehavior.NONE -> {
            true
          }
        }
      }
  }

  public enum class BlankBehavior {
    FILTER,
    ERROR,
    NONE,
  }

  /** Returns a Gradle [Provider] around the given [optional]. */
  public fun <T : Any> gradleProviderOf(optional: Optional<T>): Provider<T> =
    providers.provider(optional::get)

  public companion object {
    /** @property project The project to resolve properties for. */
    public operator fun invoke(
      project: Project,
      startParameterProperty: (String) -> Provider<String> =
        project.providers.emptyStringProvider(),
      globalLocalProperty: (String) -> Provider<String> = project.providers.emptyStringProvider(),
      localPropertyProvider: (String) -> Provider<String> = project::localProperty,
      localGradlePropertyProvider: (String) -> Provider<String> = project::localGradleProperty,
      globalGradleLocalProperty: (String) -> Provider<String> = project.providers::gradleProperty,
    ): PropertyResolver =
      PropertyResolver(
        providers =
          object : SimpleProviderFactory {
            override fun <T : Any> provider(callable: Callable<T>): Provider<T> {
              return project.providers.provider(callable)
            }
          },
        startParameterProperty = startParameterProperty,
        globalLocalProperty = globalLocalProperty,
        localPropertyProvider = localPropertyProvider,
        localGradlePropertyProvider = localGradlePropertyProvider,
        globalGradleLocalProperty = globalGradleLocalProperty,
      )

    /** @property project The project to resolve properties for. */
    public operator fun invoke(
      project: Project,
      globalResolver: PropertyResolver,
      localPropertyProvider: (String) -> Provider<String> = project::localProperty,
      localGradlePropertyProvider: (String) -> Provider<String> = project::localGradleProperty,
    ): PropertyResolver =
      PropertyResolver(
        providers =
          object : SimpleProviderFactory {
            override fun <T : Any> provider(callable: Callable<T>): Provider<T> {
              return project.providers.provider(callable)
            }
          },
        startParameterProperty = globalResolver.startParameterProperty,
        globalLocalProperty = globalResolver.globalLocalProperty,
        globalGradleLocalProperty = globalResolver.globalGradleLocalProperty,
        localPropertyProvider = localPropertyProvider,
        localGradlePropertyProvider = localGradlePropertyProvider,
      )

    /** @property project The project to resolve properties for. */
    public fun createForRootProject(
      project: Project,
      startParameterProperty: (String) -> Provider<String> =
        project.providers.emptyStringProvider(),
      globalLocalProperty: (String) -> Provider<String> = project.providers.emptyStringProvider(),
    ): PropertyResolver {
      check(project.rootProject == project) {
        "Project '${project.path}' should be the root project"
      }
      val gradlePropertyProvider: (String) -> Provider<String> = project.providers::gradleProperty
      return PropertyResolver(
        providers =
          object : SimpleProviderFactory {
            override fun <T : Any> provider(callable: Callable<T>): Provider<T> {
              return project.providers.provider(callable)
            }
          },
        startParameterProperty = startParameterProperty,
        globalLocalProperty = globalLocalProperty,
        localPropertyProvider = globalLocalProperty,
        localGradlePropertyProvider = gradlePropertyProvider,
        globalGradleLocalProperty = gradlePropertyProvider,
      )
    }
  }
}

public fun ProviderFactory.emptyStringProvider(): (String) -> Provider<String> = {
  provider { null }
}
