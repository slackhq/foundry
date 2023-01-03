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
package slack.gradle.agphandler.v80

import com.android.build.api.dsl.SettingsExtension
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.initialization.Settings
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import slack.gradle.agp.AgpSettingsHandler
import slack.gradle.agp.getVersionsCatalog

internal class AgpSettingsHandler80 : AgpSettingsHandler {
  private fun VersionCatalog.getVersionInt(name: String): Int {
    return findVersion(name)
      .map { it.toString().toInt() }
      .orElseThrow {
        IllegalStateException("'$name' must be defined in libs.versions.toml versions!")
      }
  }

  override fun apply(settings: Settings) {
    val catalog = settings.getVersionsCatalog()
    settings.apply(plugin = "com.android.settings")
    settings.configure<SettingsExtension> {
      compileSdk = catalog.getVersionInt("compileSdk")
      minSdk = catalog.getVersionInt("minSdk")

      // TODO support execution profiles
      //  https://developer.android.com/studio/preview/features#tool_execution_profiles
    }
  }
}
