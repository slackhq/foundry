package com.slack.sgp.intellij

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import java.time.LocalDate

// Define a regular expression that matches a date in "yyyy-mm-dd" format
private val LOCAL_DATE_REGEX = "^\\d{4}-\\d{2}-\\d{2}\$".toRegex()

// Define an extension function for the String class to check if a string can be parsed as a date
private val String.isLocalDate: Boolean
  get() {
    return LOCAL_DATE_REGEX.matches(this)
  }

@State(name = "ChangelogPresenter", storages = [Storage("ChangelogPresenter.xml")])
@Service(Service.Level.PROJECT)
class ChangelogPresenter : PersistentStateComponent<ChangelogPresenter.State> {
  data class State(var haveRead: Boolean = false)

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

    if (!myState.haveRead) {
      return PresentedChangelog(changeLogString)
    }

    for (line in changeLogString.lines()) {
      if (line.isLocalDate) {
        println("came in here")
        if (!myState.haveRead) {
          myState.haveRead = true
          println(myState.haveRead)
          lastRead = LocalDate.parse(line)
          return PresentedChangelog(changeLogString)
        }
        val tempDate = LocalDate.parse(line)
        if (tempDate == lastRead) {
          return null
        }
        lastRead = LocalDate.parse(line)
        println(lastRead)
      }
    }
    return PresentedChangelog(changeLogString)
  }

  data class PresentedChangelog(val changeLogString: String?)
}
