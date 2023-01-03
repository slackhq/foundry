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

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.plugins.ExtensionAware
import slack.gradle.agp.AgpHandler
import slack.gradle.agp.AgpHandlerFactory
import slack.gradle.settings.SgpSettingsPlugin.Companion.AGP_HANDLER_EXTENSION_KEY

public class SgpSettingsPlugin : Plugin<Settings> {
  override fun apply(settings: Settings) {
    val agpHandlerFactory = AgpHandlerFactory.load()
    agpHandlerFactory.createSettingsHandler().apply(settings)
    val handler = agpHandlerFactory.createHandler()

    // as Project objects cannot query for the Settings object (and its extensions), we
    // deposit the handler instance into each project using the extra Properties.
    settings.gradle.beforeProject {
      extensions.extraProperties[AGP_HANDLER_EXTENSION_KEY] = handler
    }
  }

  internal companion object {
    const val AGP_HANDLER_EXTENSION_KEY: String = "_sgp_agphandler"
  }
}

/** Retrieves the cached [AgpHandler] from extras. This is deposited here by [SgpSettingsPlugin]. */
internal fun ExtensionAware.agpHandler(): AgpHandler {
  val properties = extensions.extraProperties
  if (properties.has(AGP_HANDLER_EXTENSION_KEY)) {
    return properties.get(AGP_HANDLER_EXTENSION_KEY) as AgpHandler
  } else {
    error(
      "No AgpHandler found in extraProperties! Did you forget to apply the SGP settings plugin?"
    )
  }
}
