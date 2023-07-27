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
import java.nio.file.Paths

/** Manages user-specific settings for the Skate plugin */
@Service(Service.Level.PROJECT)
@State(name = "SkatePluginSettings", storages = [Storage("skate.xml")])
class SkatePluginSettings(private val project: Project) :
  SimplePersistentStateComponent<SkatePluginSettings.State>(State()) {

  var whatsNewFilePath: String
    get() = state.whatsNewFilePath ?: "WHATSNEW.md"
    set(value) {
      val file = Paths.get(value)
      val projectDir = Paths.get(project.basePath!!)

      // If the file is in the project directory, just store the filename
      if (file.startsWith(projectDir)) {
        state.whatsNewFilePath = file.fileName.toString()
      } else {
        // Otherwise, store the full path
        state.whatsNewFilePath = file.toAbsolutePath().toString()
      }
    }

  var isWhatsNewEnabled: Boolean
    get() = state.isWhatsNewEnabled
    set(value) {
      state.isWhatsNewEnabled = value
    }

  class State : BaseState() {
    var whatsNewFilePath by string()
    var isWhatsNewEnabled by property(true)
  }
}
