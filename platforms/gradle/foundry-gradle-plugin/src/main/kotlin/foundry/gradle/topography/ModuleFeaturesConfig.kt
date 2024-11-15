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
package foundry.gradle.topography

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import foundry.common.json.JsonTools
import foundry.common.json.JsonTools.readJsonValueMap
import java.nio.file.Path

/**
 * Represents a configuration for module features that can be JSON-encoded.
 *
 * @property _features the set of user-defined [features][ModuleFeature].
 * @property _buildUponDefaults indicates whether these should build upon the [DefaultFeatures] set.
 * @property _defaultFeatureOverrides adhoc overrides of default feature values. These should be a
 *   subset of [ModuleFeature] properties and will be overlaid onto them
 */
@Suppress("PropertyName")
@JsonClass(generateAdapter = true)
internal data class ModuleFeaturesConfig(
  @Json(name = "features") val _features: Set<ModuleFeature> = emptySet(),
  @Json(name = "buildUponDefaults") val _buildUponDefaults: Boolean = true,
  @Json(name = "defaultFeatureOverrides")
  val _defaultFeatureOverrides: List<Map<String, Any>> = emptyList(),
) {

  fun loadFeatures(): Map<String, ModuleFeature> {
    val inputFeatures = _features.associateBy { it.name }
    val defaultFeatures: Map<String, ModuleFeature> =
      if (_buildUponDefaults) {
        val defaults = DefaultFeatures.load()
        buildMap {
          putAll(defaults)
          for (override in _defaultFeatureOverrides) {
            val overrideName =
              override["name"] as? String?
                ?: error("No feature name defined in override '$override'")
            val defaultToOverride =
              defaults[overrideName] ?: error("No default feature found for '$overrideName'")
            // To simply do this, we just finagle the default to a JSON map and then overlay the new
            // one onto it
            val defaultJsonValueMap = JsonTools.toJsonBuffer(defaultToOverride).readJsonValueMap()
            val newJsonValueMap = defaultJsonValueMap + override
            val newFeature = JsonTools.fromJsonValue<ModuleFeature>(newJsonValueMap)
            put(overrideName, newFeature)
          }
        }
      } else {
        emptyMap()
      }
    return defaultFeatures + inputFeatures
  }

  companion object {
    val DEFAULT = ModuleFeaturesConfig()

    fun load(path: Path): ModuleFeaturesConfig {
      return JsonTools.fromJson(path)
    }
  }
}
