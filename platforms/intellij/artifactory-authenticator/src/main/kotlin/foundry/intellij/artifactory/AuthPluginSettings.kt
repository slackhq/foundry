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
package foundry.intellij.artifactory

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
    get() =
      state.url?.let {
        if (state.version == null) {
          // Handle legacy non-encoded data when version is absent
          it
        } else {
          try {
            it.decodeBase64()
          } catch (_: Exception) {
            // Can't decode, null out the value
            state.url = null
            null
          }
        }
      }
    set(value) {
      val previous = url
      state.url = value?.encodeBase64()
      notifyChange(previous != value)
    }

  var username: String?
    get() =
      state.username?.let {
        if (state.version == null) {
          // Handle legacy non-encoded data when version is absent
          it
        } else {
          try {
            it.decodeBase64()
          } catch (_: Exception) {
            // Can't decode, null out the value
            state.username = null
            null
          }
        }
      }
    set(value) {
      val previous = username
      state.username = value?.encodeBase64()
      notifyChange(previous != value)
    }

  var token: String?
    get() =
      state.token?.let {
        if (state.version == null) {
          // Handle legacy non-encoded data when version is absent
          it
        } else {
          try {
            it.decodeBase64()
          } catch (_: Exception) {
            // Can't decode, null out the value
            state.token = null
            null
          }
        }
      }
    set(value) {
      val previous = token
      state.token = value?.encodeBase64()
      notifyChange(previous != value)
    }

  private fun notifyChange(changed: Boolean) {
    state.version = CURRENT_VERSION
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
    var version by string()
  }
}
