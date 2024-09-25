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
package foundry.intellij.skate

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.OptionTag
import java.time.LocalDate

@State(name = "ChangelogJournal", storages = [Storage("ChangelogJournal.xml")])
@Service(Service.Level.PROJECT)
class ChangelogJournal : PersistentStateComponent<ChangelogJournal> {

  @OptionTag(converter = LocalDateConverter::class) var lastReadDate: LocalDate? = null

  companion object {
    fun getInstance(): ChangelogJournal {
      return ChangelogJournal()
    }
  }

  override fun getState(): ChangelogJournal {
    return this
  }

  override fun loadState(state: ChangelogJournal) {
    XmlSerializerUtil.copyBean(state, this)
  }
}
