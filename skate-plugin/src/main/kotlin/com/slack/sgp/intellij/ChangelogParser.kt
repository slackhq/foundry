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

import com.slack.sgp.intellij.util.memoized
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object ChangelogParser {
  // Define a regular expression that matches a date in "yyyy-mm-dd" format
  private val LOCAL_DATE_REGEX = "^\\d{4}-\\d{2}-\\d{2}$".toRegex()

  private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  // Define an extension function for the String class to check if a string can be parsed as a date
  private val String.isLocalDate: Boolean
    get() {
      return LOCAL_DATE_REGEX.matches(this)
    }

  /**
   * Function to parse a changelog and filter it based on a provided previous date entry.
   *
   * @param changelogContent The entire changelog content as a string.
   * @param lastReadDate The date of the previous seen entry, can be null.
   * @return A [PresentedChangelog] object containing the filtered changelog and the date of the
   *   latest entry.
   */
  fun readFile(changelogContent: String, lastReadDate: LocalDate?): PresentedChangelog {
    val newTime = LocalDate.now()
    if (changelogContent.isBlank()) {
      return PresentedChangelog(null, newTime)
    }

    // Lazily evaluated sequence of changelog sections
    val sectionsSequence =
      changelogContent
        .lineSequence()
        .partitionByDelimiter { line ->
          if (line.isLocalDate) {
            try {
              LocalDate.parse(line, DATE_FORMATTER)
            } catch (e: DateTimeParseException) {
              null
            }
          } else {
            null
          }
        }
        .memoized()

    val sections =
      if (lastReadDate == null) {
        sectionsSequence.take(1)
      } else {
        sectionsSequence.takeWhile { section -> section.date > lastReadDate }
      }

    val sectionsList = sections.toList()
    return if (sectionsList.isEmpty()) {
      // Second read is ok as it's memoized
      PresentedChangelog(null, sectionsSequence.firstOrNull()?.date)
    } else {
      PresentedChangelog(sectionsList.joinToString("\n\n") { it.content }, sectionsList[0].date)
    }
  }

  /** Partitions a given sequence into [ChangelogSection]s based on a given [dateParser]. */
  private fun Sequence<String>.partitionByDelimiter(
    dateParser: (line: String) -> LocalDate?
  ): Sequence<ChangelogSection> = sequence {
    val iterator = iterator()
    val currentPartition = StringBuilder()
    var currentPartitionDate: LocalDate? = null

    while (iterator.hasNext()) {
      val line = iterator.next()

      val parsedDate = dateParser(line)
      if (parsedDate != null) {
        if (currentPartition.isNotEmpty() && currentPartitionDate != null) {
          yield(ChangelogSection(currentPartitionDate, currentPartition.toString().trim()))
          currentPartition.clear()
        }
        currentPartition.appendLine(line)
        currentPartitionDate = parsedDate
      } else {
        currentPartition.appendLine(line)
      }
    }

    if (currentPartition.isNotEmpty()) {
      if (currentPartitionDate != null) {
        yield(ChangelogSection(currentPartitionDate, currentPartition.toString().trim()))
      } else {
        // Un-delimited changelog, probably just the title. Yield nothing
        println("Un-delimited changelog, probably just the title.")
      }
    }
  }

  /** Represents a parsed [changeLogString] to present up to the given [lastReadDate]. */
  data class PresentedChangelog(val changeLogString: String?, val lastReadDate: LocalDate?)

  /** Represents a dated section of a changelog. */
  private data class ChangelogSection(val date: LocalDate, val content: String)
}
