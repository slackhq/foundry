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
package com.slack.sgp.intellij.util

import java.nio.file.Path

fun Path.getJavaPackageName(): String? {
  val kotlinPathIndex =
    (0 until nameCount - 2).find {
      getName(it).toString() == "src" &&
        (getName(it + 1).toString() == "main" || getName(it + 1).toString() == "test") &&
        getName(it + 2).toString() == "kotlin"
    } ?: return null

  val endIndex = if (getName(nameCount - 1).toString().contains(".")) nameCount - 1 else nameCount
  val packagePath = subpath(kotlinPathIndex + 3, endIndex)
  return packagePath.joinToString(".")
}
