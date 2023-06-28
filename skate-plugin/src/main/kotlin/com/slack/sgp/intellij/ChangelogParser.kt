package com.slack.sgp.intellij

import java.time.LocalDate

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

    // no previous entry in the changelog
    // previous entry is found, if found, check if it's the first entry or if there's other content
    // since
    var previous: LocalDate? = null
    var breakOnNextDate = false
    var entryCount = 0
    val changeLogSubstring =
      buildString {
          var currentBlock = StringBuilder()
          for (line in changeLogString.lines()) {
            if (line.isLocalDate) {
              val localDate = LocalDate.parse(line)
              if (breakOnNextDate) {
                break
              }
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
              if (localDate == previousEntry) {
                breakOnNextDate = true
              } else {
                entryCount++
              }
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
