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

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "SkatePluginSettings", storages = [Storage("skate.xml")])
class SkatePluginSettings(
  private val project: Project,
) : SimplePersistentStateComponent<SkatePluginSettings.State>(State()) {

  var whatsNewFilePath: String
    get() = state.whatsNewFilePath ?: "CHANGELOG.md"
    set(value) {
      state.whatsNewFilePath = value
    }

  var isWhatsNewEnabled: Boolean
    get() = state.isWhatsNewEnabled
    set(value) {
      state.isWhatsNewEnabled = value
    }

  class State : BaseState() {
    // TODO: make it enabled or disabled based on isWhatsNewEnabled
    var whatsNewFilePath by string()
    var isWhatsNewEnabled by property(true)
  }
}
