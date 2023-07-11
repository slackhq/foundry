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
  // instead of storing a boolean, store a date of last read
  var lastRead: LocalDate? = null

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

    var entryCount = 0

    // if the previous entry does not match the latest entry:
    val changeLogSubstring =
      buildString {
          var currentBlock = StringBuilder()
          for (line in changeLogString.lines()) {
            if (line.isLocalDate) {
              val localDate = localDateConverter.fromString(line)

              // if a date was previously encountered, append the block to the final string:
              if (myState.lastReadDate != null) {
                if (localDate == myState.lastReadDate) {
                  break
                }
                if (entryCount == 0) {
                  append(currentBlock.toString())
                }
              }
              entryCount++
              currentBlock = StringBuilder()
              currentBlock.appendLine(line)
            } else {
              currentBlock.appendLine(line)
            }
          }

          append(currentBlock.toString())
        }
        .trimEnd()

    return PresentedChangelog(changeLogSubstring)
  }

  data class PresentedChangelog(val changeLogString: String?)
}
