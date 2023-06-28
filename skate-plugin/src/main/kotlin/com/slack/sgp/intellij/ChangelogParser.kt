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
package com.slack.sgp.intellij

import java.time.LocalDate

/**
 * Reads a CHANGELOG.md file and produces a subset of it, based on a specified previous entry date.
 *
 * If a previous entry date is supplied and is found within the changelog, this function will return
 * a new changelog that contains entries only up to and including this date. If the previous entry
 * date is not found, the entire changelog is returned.
 *
 * @param changeLogString The entire changelog, as a string, where each entry is expected to start
 *   with a date line.
 * @param previousEntry The date of the previous entry. Can be null, in which case the entire
 *   changelog is returned.
 * @return A ParseResult object containing the filtered changelog string and the date of the latest
 *   entry.
 */
private val LOCAL_DATE_REGEX = "^\\d{4}-\\d{2}-\\d{2}\$".toRegex()
private val String.isLocalDate: Boolean
  get() {
    return LOCAL_DATE_REGEX.matches(this)
  }

object ChangelogParser {
  fun readFile(changeLogString: String, previousEntry: LocalDate? = null): ParseResult {
    /*
    date format: yyyy-mm-dd
    */

    if (previousEntry != null && !changeLogString.contains(previousEntry.toString())) {
      return ParseResult(
        changeLogString,
        LocalDate.parse(changeLogString.lines().firstOrNull { it.isLocalDate })
      )
    }

    var previous: LocalDate? = null
    var entryCount = 0
    val changeLogSubstring =
      buildString {
          var currentBlock = StringBuilder()
          for (line in changeLogString.lines()) {
            if (line.isLocalDate) {
              val localDate = LocalDate.parse(line)
              if (localDate == previousEntry) {
                break
              }
              if (previous != null) {
                append(currentBlock.toString())
              }
              currentBlock = StringBuilder()
              if (previous == null) {
                previous = localDate
              }
              entryCount++
            }
            currentBlock.appendLine(line)
          }
          if (entryCount == 0) {
            append(currentBlock.toString())
          }
        }
        .trimEnd()
    return ParseResult(changeLogSubstring.takeIf { it.isNotBlank() }, previous ?: LocalDate.now())
  }

  data class ParseResult(val changeLogString: String?, val latestEntry: LocalDate)
}
