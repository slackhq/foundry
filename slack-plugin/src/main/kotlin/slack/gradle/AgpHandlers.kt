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

import java.util.ServiceLoader
import slack.gradle.agp.AgpHandler
import slack.gradle.agp.AgpHandlerFactory
import slack.gradle.agp.AgpSettingsHandler
import slack.gradle.agp.VersionNumber

internal object AgpHandlers {
  private fun factory(): AgpHandlerFactory {
    /** Load handlers and pick the highest compatible version (by [AgpHandlerFactory.minVersion]) */
    return ServiceLoader.load(AgpHandlerFactory::class.java)
      .iterator()
      .asSequence()
      .mapNotNull { factory ->
        // Filter out any factories that can't compute the AGP version, as
        // they're _definitely_ not compatible
        try {
          FactoryData(VersionNumber.parse(factory.currentVersion()), factory)
        } catch (t: Throwable) {
          null
        }
      }
      .filter { (agpVersion, factory) -> agpVersion.baseVersion >= factory.minVersion }
      .maxByOrNull { (_, factory) -> factory.minVersion }
      ?.factory
      ?: error("Unrecognized AGP version!")
  }

  fun createHandler(): AgpHandler {
    return factory().create()
  }

  fun createSettingsHandler(): AgpSettingsHandler {
    return factory().createSettingsHandler()
  }
}

private data class FactoryData(val agpVersion: VersionNumber, val factory: AgpHandlerFactory)
