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
package com.slack.sgp.intellij.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.slack.sgp.intellij.SkateBundle
import com.slack.sgp.intellij.SkatePluginSettings
import java.io.File

internal class SkateConfigUI(
  private val settings: SkatePluginSettings,
  private val project: Project,
) {

  fun createPanel(): DialogPanel = panel {
    checkBoxRow()
    choosePathRow()
  }

  private fun Panel.checkBoxRow() {
    row(SkateBundle.message("skate.configuration.enableWhatsNew.title")) {
      checkBox(
          "<html>${SkateBundle.message("skate.configuration.enableWhatsNew.description")}</html>"
        )
        .bindSelected(
          getter = { settings.isWhatsNewEnabled },
          setter = { settings.isWhatsNewEnabled = it }
        )
    }
  }

  private fun Panel.choosePathRow() {
    row(SkateBundle.message("skate.configuration.choosePath.title")) {
      textFieldWithBrowseButton(
          SkateBundle.message("skate.configuration.choosePath.dialog.title"),
          project,
          FileChoosing.singleMdFileChooserDescriptor()
        )
        .bindText(
          getter = {
            LocalFileSystem.getInstance().extractPresentableUrl(settings.whatsNewFilePath)
          },
          setter = {
            if (File(it).isFile) {
              settings.whatsNewFilePath =
                LocalFileSystem.getInstance().findFileByPath(it)?.path.orEmpty()
            } else {
              settings.whatsNewFilePath = ""
            }
          }
        )
        .enabled(settings.isWhatsNewEnabled)
    }
  }
}
