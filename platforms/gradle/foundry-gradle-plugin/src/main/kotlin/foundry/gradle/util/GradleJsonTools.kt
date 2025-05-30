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
package foundry.gradle.util

import foundry.common.json.JsonTools
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider

internal inline fun <reified T : Any> JsonTools.toJson(
  provider: Provider<out FileSystemLocation>,
  value: T?,
  prettyPrint: Boolean = false,
) {
  return toJson(provider.get().asFile, value, prettyPrint)
}
