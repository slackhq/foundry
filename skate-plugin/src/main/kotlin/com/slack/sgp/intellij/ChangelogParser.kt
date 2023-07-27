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
import java.time.format.DateTimeFormatter

// Define a regular expression that matches a date in "yyyy-mm-dd" format
private val LOCAL_DATE_REGEX = "^\\d{4}-\\d{2}-\\d{2}$".toRegex()

val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

// Define an extension function for the String class to check if a string can be parsed as a date
private val String.isLocalDate: Boolean
  get() {
    return LOCAL_DATE_REGEX.matches(this.trim('_'))
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
    // Initialize lastReadDate with updated value, or will stay null if not
    var updatedLastReadDate = lastReadDate

    // Check if the lastReadDate is null
    if (updatedLastReadDate == null) {
      var foundDate = false

      // Iterate through every line of the changeLogString
      for (line in changeLogString.lines()) {
        if (line.isLocalDate) {

          // Parse the line to be a local date
          val date: LocalDate? = LocalDate.parse(line.trim('_'), DATE_FORMATTER)

          updatedLastReadDate = date
          foundDate = true

          // Break the loop now, the first date is found
          break
        }
      }

      // If no date is found, set the lastReadDate to current date
      if (!foundDate) {
        updatedLastReadDate = LocalDate.now()
      }

      // Returning the entire changelog with updated lastReadDate
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

              // Check if the line matches the format of the date
              line.isLocalDate -> {
                // Parse line to be local date
                val localDate: LocalDate? = LocalDate.parse(line.trim('_'), DATE_FORMATTER)

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

              // Check if the line starts a new block associated with a new entry
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
