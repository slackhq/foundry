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
package slack.gradle.dependencies

public typealias Notation = Map<String, String>

public data class DependencyDef(
  val group: String,
  val artifact: String,
  val comments: String? = null,
  val ext: String? = null,
  val gradleProperty: String,
  val isBomManaged: Boolean = false,
) {
  val coordinates: Map<String, String> =
    mutableMapOf<String, String>().apply {
      put("group", group)
      put("name", artifact)
      ext?.let { put("ext", it) }
    }
  val identifier: String = "$group:$artifact"
  val extSuffix: String
    get() = ext?.let { "@$it" }.orEmpty()
}
