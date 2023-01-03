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
import slack.gradle.AgpHandlers

public class SgpSettingsPlugin : Plugin<Settings> {
  override fun apply(settings: Settings) {
    AgpHandlers.createSettingsHandler().apply(settings)
    val handler = AgpHandlers.createHandler()

    // as Project objects cannot query for the Settings object (and its extensions), we
    // deposit the handler instance into each project using the extra Properties.
    settings.gradle.beforeProject { extensions.extraProperties["_sgp_agphandler"] = handler }
  }
}
