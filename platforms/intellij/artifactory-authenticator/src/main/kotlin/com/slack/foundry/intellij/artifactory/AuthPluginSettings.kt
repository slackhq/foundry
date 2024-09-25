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
package com.slack.foundry.intellij.artifactory

import com.intellij.ide.plugins.auth.PluginRepositoryAuthListener
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(name = "ArtifactoryAuthSettings", storages = [Storage("artifactoryAuthSettings.xml")])
class AuthPluginSettings : SimplePersistentStateComponent<AuthPluginSettings.State>(State()) {

  var enabled: Boolean
    get() = state.enabled
    set(value) {
      val previous = state.enabled
      state.enabled = value
      notifyChange(previous != value)
    }

  var url: String?
    get() = state.url
    set(value) {
      val previous = state.url
      state.url = value
      notifyChange(previous != value)
    }

  var username: String?
    get() = state.username
    set(value) {
      val previous = state.username
      state.username = value
      notifyChange(previous != value)
    }

  var token: String?
    get() = state.token
    set(value) {
      val previous = state.token
      state.token = value
      notifyChange(previous != value)
    }

  private fun notifyChange(changed: Boolean) {
    if (changed) {
      // Any time we change this setting we need to notify the IDE that the auth has changed
      PluginRepositoryAuthListener.notifyAuthChanged()
    }
  }

  class State : BaseState() {
    var enabled by property(false)
    var url by string()
    var username by string()
    var token by string()
  }
}
