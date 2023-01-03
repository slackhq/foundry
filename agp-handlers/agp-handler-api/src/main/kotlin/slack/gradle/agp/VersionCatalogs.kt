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
package slack.gradle.agp

import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.getByType

public fun ExtensionAware.getVersionsCatalog(catalogName: String = "libs"): VersionCatalog {
  return getVersionsCatalogOrNull(catalogName) ?: error("No versions catalog found!")
}

public fun ExtensionAware.getVersionsCatalogOrNull(catalogName: String = "libs"): VersionCatalog? {
  return try {
    extensions.getByType<VersionCatalogsExtension>().named(catalogName)
  } catch (ignored: Exception) {
    null
  }
}
