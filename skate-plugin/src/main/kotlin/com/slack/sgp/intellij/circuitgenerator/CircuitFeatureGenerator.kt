/*
 * Copyright (C) 2024 Slack Technologies, LLC
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
package com.slack.sgp.intellij.circuitgenerator

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toNullableProperty
import com.slack.sgp.intellij.util.getJavaPackageName
import java.io.File
import slack.tooling.projectgen.CircuitComponentFactory

class CircuitFeatureGenerator : AnAction(), DumbAware {
  var featureNameField = ""
  var selectedType = "UI + Presenter"

  override fun actionPerformed(event: AnActionEvent) {
    val centerPanel = panel {
      row("Name") { textField().bindText(::featureNameField) }
      row("Template") {
        comboBox(items = listOf("UI + Presenter", "Presenter Only"))
          .bindItem(::selectedType.toNullableProperty())
      }
    }
    val dialog = dialog(title = "New Circuit Feature", panel = centerPanel)
    if (dialog.showAndGet()) {
      event.getData(CommonDataKeys.VIRTUAL_FILE)?.let { data ->
        createCircuitFeature(featureNameField, selectedType, data, event.project)
      }
    }
  }

  private fun createCircuitFeature(
    featureNameField: String,
    selectedType: String,
    data: VirtualFile,
    project: Project?,
  ) {
    val directory = if (data.isDirectory) data.path else data.parent.path
    val packageName = directory.getJavaPackageName()
    when (selectedType) {
      "UI + Presenter" ->
        CircuitComponentFactory()
          .generateCircuitAndComposeUI(directory, packageName, featureNameField)
      "Presenter Only" ->
        CircuitComponentFactory().generateCircuitPresenter(directory, packageName, featureNameField)
    }

    project?.let {
      val circuitScreenPath = File("${directory}/${featureNameField}Screen.kt")
      logger<CircuitFeatureGenerator>().info(circuitScreenPath.path)
      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(circuitScreenPath)?.let { file ->
        FileEditorManager.getInstance(it).openFile(file)
      }
    }
    LocalFileSystem.getInstance().refresh(true)
  }
}
