package com.slack.sgp.intellij

// Define a regular expression that matches a date in "yyyy-mm-dd" format
private val LOCAL_DATE_REGEX = "^\\d{4}-\\d{2}-\\d{2}\$".toRegex()

// Define an extension function for the String class to check if a string can be parsed as a date
private val String.isLocalDate: Boolean
  get() {
    return LOCAL_DATE_REGEX.matches(this)
  }

class ChangelogPresenter {
  var haveRead: Boolean = false
  // instead of storing a boolean, store a date of last read

  fun readFile(changeLogString: String): String? {
    if (!haveRead) {
      haveRead = true
      return changeLogString
    }
    return null

    //    // If previousEntry is not null and it is not contained in changeLogString
    //    // the function will return the changeLogString and the first date found
    //    if (previousEntry != null && !changeLogString.contains(previousEntry.toString())) {
    //      return ParseResult(
    //        changeLogString,
    //        LocalDate.parse(changeLogString.lines().firstOrNull { it.isLocalDate })
    //      )
    //    }
    //
    //    // previous is used to store the last date encountered
    //    var previous: LocalDate? = null
    //
    //    // entryCount is used to track the number of date entries encountered
    //    var entryCount = 0
    //
    //    // changeLogSubstring is the filtered changeLogString
    //    // buildString is used to append entries and dates to a string
    //    val changeLogSubstring =
    //      buildString {
    //          var currentBlock = StringBuilder()
    //          for (line in changeLogString.lines()) {
    //
    //            // If the line is a date
    //            if (line.isLocalDate) {
    //
    //              // Parse the date
    //              val localDate = LocalDate.parse(line)
    //
    //              // If the parsed date equals the previous entry, stop the loop
    //              if (localDate == previousEntry) {
    //                break
    //              }
    //
    //              // If a date was previously encountered, append the block to the final string
    //              if (previous != null) {
    //                append(currentBlock.toString())
    //              }
    //
    //              // Reset the current block
    //              currentBlock = StringBuilder()
    //
    //              // If this is the first date encountered, set it as previous
    //              if (previous == null) {
    //                previous = localDate
    //              }
    //
    //              // Increment the entry count
    //              entryCount++
    //            }
    //
    //            // Append the line to the current block
    //            currentBlock.appendLine(line)
    //          }
    //
    //          // If no date entries were encountered, append the block to the final string
    //          if (entryCount == 0) {
    //            append(currentBlock.toString())
    //          }
    //        }
    //        .trimEnd()
    //
    //    // If the changelog substring is blank, set it as null
    //    // If the previous entry equals to the latest entry, or if no date is found,
    //    // use the previous entry date as the latest date instead of the current date.
    //    return ParseResult(
    //      changeLogSubstring.takeIf { it.isNotBlank() },
    //      previous ?: previousEntry ?: LocalDate.now()
    //    )
  }

  // Define the class ParseResult to return the filtered changelog and the latest date entry
  //  data class ParseResult(val changeLogString: String?)
}
