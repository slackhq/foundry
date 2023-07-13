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

import LocalDateConverter
import java.time.LocalDate

val localDateConverter = LocalDateConverter()

// Define a regular expression that matches a date in "yyyy-mm-dd" format
private val LOCAL_DATE_REGEX = "^_\\d{4}-\\d{2}-\\d{2}_$".toRegex()

// Define an extension function for the String class to check if a string can be parsed as a date
private val String.isLocalDate: Boolean
  get() {
    return LOCAL_DATE_REGEX.matches(this)
  }

private val VERSION_PATTERN_REGEX = "\\d+\\.\\d+\\.\\d+".toRegex()

private val String.startsNewBlock: Boolean
  get() {
    return VERSION_PATTERN_REGEX.matches(this.trim())
  }

object ChangelogParser {
  /**
   * Function to parse a changelog and filter it based on a provided previous date entry.
   *
   * @param changeLogString The entire changelog as a string.
   * @param previousEntry The date of the previous entry, can be null.
   * @return A ParseResult object containing the filtered changelog and the date of the latest
   *   entry.
   */
  fun readFile(changeLogString: String, lastReadDate: LocalDate? = null): PresentedChangelog {

    var updatedLastReadDate = lastReadDate

    if (updatedLastReadDate == null) {
      var foundDate = false

      for (line in changeLogString.lines()) {
        if (line.isLocalDate) {
          val date = localDateConverter.fromString(line)
          updatedLastReadDate = date
          foundDate = true

          break
        }
      }
      if (!foundDate) {
        updatedLastReadDate = LocalDate.now()
      }
      return PresentedChangelog(changeLogString, updatedLastReadDate)
    }

    val changeLogSubstring =
      buildString {
          var currentBlock = StringBuilder()
          var blockDate: LocalDate? = null
          var inHeader = true
          var firstNewDate: LocalDate? = null

          for (line in changeLogString.lines()) {
            when {
              line.isLocalDate -> {
                val localDate = localDateConverter.fromString(line)
                if (localDate!! > updatedLastReadDate!!) {
                  blockDate = localDate
                  currentBlock.appendLine(line)
                  if (firstNewDate == null) {
                    firstNewDate = localDate
                  }
                } else {
                  break
                }
              }
              line.startsNewBlock -> {
                if (!inHeader) {
                  if (blockDate != null && currentBlock.isNotBlank()) {
                    append(currentBlock.toString())
                    currentBlock = StringBuilder()
                  }
                }
                inHeader = false
                currentBlock.appendLine(line)
              }
              else -> {
                currentBlock.appendLine(line)
              }
            }
          }
          if (firstNewDate != null) {
            updatedLastReadDate = firstNewDate
          }
        }
        .trim()

    return PresentedChangelog(changeLogSubstring, updatedLastReadDate)
  }

  data class PresentedChangelog(val changeLogString: String?, val lastReadDate: LocalDate?)
}
