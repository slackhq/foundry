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
package com.slack.sgp.common

import okio.FileSystem
import okio.Path

public fun Path.prepareForGradleOutput(fs: FileSystem): Path = apply {
  if (fs.exists(this)) fs.delete(this)
  parent?.let(fs::createDirectories)
}

public fun Path.readLines(fs: FileSystem): List<String> {
  return fs.read(this) {
    val lines = mutableListOf<String>()
    while (!exhausted()) {
      readUtf8Line()?.let { lines += it }
    }
    lines
  }
}

public fun Path.writeLines(lines: Iterable<String>, fs: FileSystem) {
  fs.write(this) {
    lines.forEach { line ->
      writeUtf8(line)
      writeUtf8("\n")
    }
  }
}

public fun Path.writeText(text: String, fs: FileSystem) {
  fs.write(this) { writeUtf8(text) }
}
