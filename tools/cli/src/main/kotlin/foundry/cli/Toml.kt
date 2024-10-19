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
package foundry.cli

import java.io.File

public object Toml {
  private val IGNORED_BLOCKS =
    sequenceOf("plugins", "libraries", "bundles").mapTo(LinkedHashSet()) { "[$it]" }

  /**
   * Parses the version declarations out of a given Gradle versions TOML file. This tries to be
   * efficient by only parsing the first part of the file and assumes that plugins are the next
   * block after the version declarations.
   */
  public fun parseVersion(versionsToml: File): Map<String, String> {
    return versionsToml.useLines(Charsets.UTF_8, ::parseVersion)
  }

  internal fun parseVersion(lines: Sequence<String>): Map<String, String> {
    return lines
      .filterNot { it.startsWith('#') || it.trim() == "[versions]" }
      .takeWhile { it.trim() !in IGNORED_BLOCKS }
      .filterNot { it.isBlank() }
      .associate { line ->
        val (k, v) = line.substringBefore("#").split("=").map { it.trim().removeSurrounding("\"") }
        k to v
      }
  }
}
