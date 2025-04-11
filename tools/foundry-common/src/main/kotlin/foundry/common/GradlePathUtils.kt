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

import com.google.common.base.CaseFormat


private fun kebabCaseToCamelCase(s: String): String {
  return CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, s)
}

/**
 * Returns a project accessor representation of the given [projectPath].
 *
 * Example: `:libraries:foundation` -> `libraries.foundation`.
 */
public fun convertProjectPathToAccessor(projectPath: String): String {
  return projectPath.removePrefix(":").split(":").joinToString(separator = ".") { segment ->
    kebabCaseToCamelCase(segment)
  }
}

//public fun String.gradleProjectAccessorify(): String {
//  return buildString {
//    var capNext = false
//    for (c in this@gradleProjectAccessorify) {
//      when (c) {
//        '-' -> {
//          capNext = true
//          continue
//        }
//        ':' -> {
//          append('.')
//          continue
//        }
//        else -> {
//          append(if (capNext) c.uppercaseChar() else c)
//        }
//      }
//      capNext = false
//    }
//  }
//}
