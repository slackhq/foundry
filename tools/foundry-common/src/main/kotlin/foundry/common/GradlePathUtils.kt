/*
 * Copyright (C) 2025 Slack Technologies, LLC
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
package foundry.common

public fun String.gradleProjectAccessorify(): String {
  return buildString {
    var capNext = false
    for (c in this@gradleProjectAccessorify) {
      when (c) {
        '-' -> {
          capNext = true
          continue
        }
        ':' -> {
          append('.')
          continue
        }
        else -> {
          append(if (capNext) c.uppercaseChar() else c)
        }
      }
      capNext = false
    }
  }
}
