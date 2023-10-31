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
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.layout.ComponentPredicate
import com.slack.sgp.intellij.SkateBundle
import com.slack.sgp.intellij.SkatePluginSettings
import java.io.File

internal class SkateConfigUI(
  private val settings: SkatePluginSettings,
  private val project: Project,
) {

  fun createPanel(): DialogPanel = panel {
    whatsNewPanelSettings()
    enableProjectGenMenuAction()
    featureFlagSettings()
    tracingSettings()
  }

  private fun Panel.whatsNewPanelSettings() {
    group(SkateBundle.message("skate.configuration.whatsNewPanel.title")) {
      row {
        checkBox(SkateBundle.message("skate.configuration.enableWhatsNew.description"))
          .bindSelected(
            getter = { settings.isWhatsNewEnabled },
            setter = { settings.isWhatsNewEnabled = it }
          )
      }
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
                val absolutePath = LocalFileSystem.getInstance().findFileByPath(it)?.path.orEmpty()
                val projectPath = project.basePath ?: ""
                val relativePath = absolutePath.removePrefix(projectPath).removePrefix("/")
                settings.whatsNewFilePath = relativePath
              } else {
                settings.whatsNewFilePath = ""
              }
            }
          )
          .enabled(settings.isWhatsNewEnabled)
      }
    }
  }

  private fun Panel.enableProjectGenMenuAction() {
    group(SkateBundle.message("skate.configuration.projectGenMenuAction.title")) {
      row {
        checkBox(SkateBundle.message("skate.configuration.enableProjectGenMenuAction.description"))
          .bindSelected(
            getter = { settings.isProjectGenMenuActionEnabled },
            setter = { settings.isProjectGenMenuActionEnabled = it }
          )
      }
    }
  }

  private fun Panel.featureFlagSettings() {
    group(SkateBundle.message("skate.configuration.featureFlagAnnotator.title")) {
      lateinit var linkifiedFeatureFlagsCheckBox: Cell<JBCheckBox>

      row() {
        linkifiedFeatureFlagsCheckBox =
          checkBox(SkateBundle.message("skate.configuration.enableFeatureFlagLinking.description"))
            .bindSelected(
              getter = { settings.isLinkifiedFeatureFlagsEnabled },
              setter = { settings.isLinkifiedFeatureFlagsEnabled = it }
            )
      }

      bindAndValidateTextFieldRow(
        titleMessageKey = "skate.configuration.featureFlagFilePattern.title",
        getter = { settings.featureFlagFilePattern },
        setter = { settings.featureFlagFilePattern = it },
        errorMessageKey = "skate.configuration.featureFlagFieldEmpty.error",
        enabledCondition = linkifiedFeatureFlagsCheckBox.selected,
      )

      bindAndValidateTextFieldRow(
        titleMessageKey = "skate.configuration.featureFlagAnnotation.title",
        getter = { settings.featureFlagAnnotation },
        setter = { settings.featureFlagAnnotation = it },
        errorMessageKey = "skate.configuration.featureFlagFieldEmpty.error",
        enabledCondition = linkifiedFeatureFlagsCheckBox.selected,
      )

      bindAndValidateTextFieldRow(
        titleMessageKey = "skate.configuration.featureFlagBaseUrl.title",
        getter = { settings.featureFlagBaseUrl },
        setter = { settings.featureFlagBaseUrl = it },
        errorMessageKey = "skate.configuration.featureFlagFieldEmpty.error",
        enabledCondition = linkifiedFeatureFlagsCheckBox.selected
      )
    }
  }

  private fun Panel.tracingSettings() {
    group(SkateBundle.message("skate.configuration.tracing.title")) {
      lateinit var tracingEnabledButton: Cell<JBCheckBox>

      row {
        tracingEnabledButton =
          checkBox(SkateBundle.message("skate.configuration.enableTracing.description"))
            .bindSelected(
              getter = { settings.isTracingEnabled },
              setter = { settings.isTracingEnabled = it }
            )
      }
      bindAndValidateTextFieldRow(
        titleMessageKey = "skate.configuration.tracingEndpoint.title",
        getter = { settings.tracingEndpoint },
        setter = { settings.tracingEndpoint = it },
        errorMessageKey = "skate.configuration.tracingEndpoint.error",
        enabledCondition = tracingEnabledButton.selected,
      )
    }
  }

  private fun Panel.bindAndValidateTextFieldRow(
    titleMessageKey: String,
    getter: () -> String?,
    setter: (String) -> Unit,
    errorMessageKey: String,
    enabledCondition: ComponentPredicate? = null,
  ) {
    row(SkateBundle.message(titleMessageKey)) {
      textField()
        .bindText(getter = { getter().orEmpty() }, setter = setter)
        .validationOnApply {
          if (it.text.isBlank()) error(SkateBundle.message(errorMessageKey)) else null
        }
        .validationOnInput {
          if (it.text.isBlank()) error(SkateBundle.message(errorMessageKey)) else null
        }
        .apply { enabledCondition?.let { enabledIf(it) } }
    }
  }
}
