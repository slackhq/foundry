/*
 * Copyright (C) 2023 Slack Technologies, LLC
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
package slack.gradle.settings

import org.gradle.api.Action
import javax.inject.Inject
import org.gradle.api.Plugin
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.initialization.Settings
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.property
import slack.gradle.SlackExtensionMarker
import slack.gradle.SlackVersions
import slack.gradle.agp.AgpHandler
import slack.gradle.agp.AgpHandlerFactory
import slack.gradle.agp.getVersionsCatalog
import slack.gradle.settings.SgpSettingsPlugin.Companion.AGP_HANDLER_EXTENSION_KEY
import slack.gradle.settings.SgpSettingsPlugin.Companion.SGP_VERSIONS_EXTENSION_KEY
import slack.gradle.tomlKey

public class SgpSettingsPlugin : Plugin<Settings> {
  override fun apply(settings: Settings) {
    val catalog = settings.getVersionsCatalog()
    val extension = settings.extensions.create<SgpSettingsExtension>("slack", catalog)

    val agpHandlerFactory = AgpHandlerFactory.load()
    agpHandlerFactory.createSettingsHandler().apply(settings)
    val handler = agpHandlerFactory.createHandler()

    lateinit var versions: SlackVersions
    settings.gradle.settingsEvaluated { versions = extension.versions.finalize() }

    // as Project objects cannot query for the Settings object (and its extensions), we
    // deposit the handler instance into each project using the extra Properties.
    settings.gradle.beforeProject {
      extensions.extraProperties[SGP_VERSIONS_EXTENSION_KEY] = versions
      extensions.extraProperties[AGP_HANDLER_EXTENSION_KEY] = handler
    }
  }

  internal companion object {
    const val AGP_HANDLER_EXTENSION_KEY: String = "_sgp_agphandler"
    const val SGP_VERSIONS_EXTENSION_KEY: String = "_sgp_versions"
  }
}

/** Retrieves the cached [AgpHandler] from extras. This is deposited here by [SgpSettingsPlugin]. */
internal fun ExtensionAware.agpHandler(): AgpHandler {
  return depositedObject(AGP_HANDLER_EXTENSION_KEY)
}

/** Retrieves the cached [AgpHandler] from extras. This is deposited here by [SgpSettingsPlugin]. */
internal fun ExtensionAware.slackVersions(): SlackVersions {
  return depositedObject(SGP_VERSIONS_EXTENSION_KEY)
}

private inline fun <reified T> ExtensionAware.depositedObject(key: String): T {
  val properties = extensions.extraProperties
  if (properties.has(key)) {
    return properties.get(key) as T
  } else {
    error(
      "No '$key' key found in extraProperties! Did you forget to apply the SGP settings plugin?"
    )
  }
}

@SlackExtensionMarker
public abstract class SgpSettingsExtension
@Inject
constructor(
  objects: ObjectFactory,
  catalog: VersionCatalog,
) {
  public val versions: SgpSettingsVersions = objects.newInstance(catalog)

  public fun versions(action: Action<SgpSettingsVersions>) {
    action.execute(versions)
  }
}

public abstract class SgpSettingsVersions
@Inject
constructor(
  private val providers: ProviderFactory,
  objects: ObjectFactory,
  private val catalog: VersionCatalog,
) {
  public val agp: Property<String> =
    objects.property<String>().convention(optionalVersionProvider("agp"))
  public val composeCompiler: Property<String> =
    objects.property<String>().convention(optionalVersionProvider("compose-compiler"))
  public val composeCompilerKotlinVersion: Property<String> =
    objects
      .property<String>()
      .convention(optionalVersionProvider("compose-compiler-kotlin-version"))
  public val detekt: Property<String> =
    objects.property<String>().convention(optionalVersionProvider("detekt"))
  public val gjf: Property<String> =
    objects.property<String>().convention(optionalVersionProvider("google-java-format"))
  public val gson: Property<String> =
    objects.property<String>().convention(optionalVersionProvider("gson"))
  public val kotlin: Property<String> =
    objects.property<String>().convention(requiredVersionProvider("kotlin"))
  public val ktlint: Property<String> =
    objects.property<String>().convention(optionalVersionProvider("ktlint"))
  public val ktfmt: Property<String> =
    objects.property<String>().convention(optionalVersionProvider("ktfmt"))
  public val objenesis: Property<String> =
    objects.property<String>().convention(optionalVersionProvider("objenesis"))
  public val jdk: Property<Int> =
    objects.property<Int>().convention(requiredVersionProvider("jdk").map(String::toInt))
  public val jvmTarget: Property<Int> =
    objects.property<Int>().convention(requiredVersionProvider("jvmTarget").map(String::toInt))

  // TODO eventually somehow define an API for bundles?

  internal fun requiredVersionProvider(
    key: String,
    defaultValue: String? = null
  ): Provider<String> {
    return optionalVersionProvider(key).let { provider ->
      defaultValue?.let { provider.orElse(it) }
        ?: provider.orElse(
          providers.provider {
            throw IllegalStateException("No catalog version found for ${tomlKey(key)}")
          }
        )
    }
  }

  internal fun optionalVersionProvider(key: String): Provider<String> {
    val tomlKey = tomlKey(key)
    return providers.provider { catalog.findVersion(tomlKey).map { it.toString() }.orElse(null) }
  }

  internal fun finalize(): SlackVersions {
    return SlackVersions(catalog, this)
  }
}
