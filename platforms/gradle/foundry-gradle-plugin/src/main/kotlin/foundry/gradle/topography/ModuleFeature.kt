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

import com.squareup.moshi.JsonClass
import foundry.common.RegexMap

@JsonClass(generateAdapter = true)
public data class ModuleFeature(
  val name: String,
  val explanation: String,
  val advice: String,
  val replacementPatterns: RegexMap = RegexMap(),
  /**
   * Generated sources root dir relative to the project dir, if any. Files are checked recursively.
   */
  val generatedSourcesDir: String? = null,
  val generatedSourcesExtensions: Set<String> = emptySet(),
  val matchingText: Set<String> = emptySet(),
  val matchingTextFileExtensions: Set<String> = emptySet(),
  /**
   * If specified, looks for any sources in this dir relative to the project dir. Files are checked
   * recursively.
   */
  val matchingSourcesDir: String? = null,
  val matchingPlugin: String? = null,
)
