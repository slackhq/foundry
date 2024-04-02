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
package com.slack.intellij.artifactory

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.COLUMNS_SHORT
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import javax.swing.JPasswordField

class AuthConfig :
  BoundSearchableConfigurable(
    displayName = AuthBundle.message("artifactoryAuth.configuration.title"),
    helpTopic = AuthBundle.message("artifactoryAuth.configuration.title"),
    _id = "com.slack.intellij.artifactory",
  ) {
  private val settings =
    ApplicationManager.getApplication().getService(AuthPluginSettings::class.java)

  override fun createPanel(): DialogPanel = panel {
    resizableColumn()
    group(AuthBundle.message("artifactoryAuth.configuration.title")) {
      lateinit var checkboxItem: Cell<JBCheckBox>
      row {
        checkboxItem =
          checkBox("Enabled")
            .bindSelected(getter = { settings.enabled }, setter = { settings.enabled = it })
      }
      row(AuthBundle.message("artifactoryAuth.configuration.url.title")) {
        textField()
          .enabledIf(checkboxItem.selected)
          .bindText(getter = { settings.url.orEmpty() }, setter = { settings.url = it })
          .validationInfo { text ->
            if (text.text.isEmpty()) {
              error("URL cannot be empty")
            } else {
              null
            }
          }
      }
      row(AuthBundle.message("artifactoryAuth.configuration.username.title")) {
        textField()
          .enabledIf(checkboxItem.selected)
          .bindText(getter = { settings.username.orEmpty() }, setter = { settings.username = it })
      }
      row(AuthBundle.message("artifactoryAuth.configuration.token.title")) {
        cell(JPasswordField())
          .columns(COLUMNS_SHORT)
          .enabledIf(checkboxItem.selected)
          .bindText(getter = { settings.token.orEmpty() }, setter = { settings.token = it })
      }
    }
  }
}
