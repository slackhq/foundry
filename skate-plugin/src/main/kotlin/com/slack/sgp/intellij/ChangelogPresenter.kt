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
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.OptionTag
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

@State(name = "ChangelogPresenter", storages = [Storage("ChangelogPresenter.xml")])
@Service(Service.Level.PROJECT)
class ChangelogPresenter :
  PersistentStateComponent<com.slack.sgp.intellij.ChangelogPresenter.State> {
  data class State(
    @OptionTag(converter = LocalDateConverter::class) var lastReadDate: LocalDate? = null
  )

  private var myState = State()

  override fun getState(): State {
    return myState
  }

  override fun loadState(state: State) {
    XmlSerializerUtil.copyBean(state, myState)
  }

  fun readFile(changeLogString: String): PresentedChangelog? {

    if (changeLogString.isEmpty()) {
      return null
    }

    // when there is no previous entry stored in the state:
    if (myState.lastReadDate == null) {
      println("it's null")
      println(myState.lastReadDate)
      for (line in changeLogString.lines()) {
        if (line.isLocalDate) {
          val date = localDateConverter.fromString(line)
          myState.lastReadDate = date
          break
        }
      }
      return PresentedChangelog(changeLogString)
    }

    // if the previous entry does not match the latest entry:
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
                if (localDate!! > myState.lastReadDate!!) {
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
                  // Append the current block to the final string if the block's date is newer than
                  // the last read date
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
          myState.lastReadDate = firstNewDate // update myState.lastReadDate here
        }
        .trim()

    return PresentedChangelog(changeLogSubstring)
  }

  data class PresentedChangelog(val changeLogString: String?)
}
