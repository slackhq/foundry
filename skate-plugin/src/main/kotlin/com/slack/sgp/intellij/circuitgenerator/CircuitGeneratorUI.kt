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

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.slack.sgp.intellij.util.circuitPresenterBaseTest
import com.slack.sgp.intellij.util.circuitUiBaseTest
import java.nio.file.Path
import javax.swing.Action
import javax.swing.JComponent
import slack.tooling.projectgen.circuitgen.CircuitGenUi
import slack.tooling.projectgen.circuitgen.FileGenerationListener

class CircuitGeneratorUi(
  dialogTitle: String,
  private val project: Project,
  private val selectedDir: Path,
  private val listener: FileGenerationListener,
) : DialogWrapper(project), CircuitGenUi.Events {

  init {
    title = dialogTitle
    init()
  }

  override fun createCenterPanel(): JComponent {
    setSize(600, 600)
    val baseTestClass =
      mapOf(
        "UiTest" to project.circuitUiBaseTest(),
        "PresenterTest" to project.circuitPresenterBaseTest(),
      )
    return CircuitGenUi()
      .createPanel(
        selectedDir,
        baseTestClass,
        this,
        listener,
      )
  }

  /* Disable default OK and Cancel action button in Dialog window. */
  override fun createActions(): Array<Action> = emptyArray()

  override fun doCancelAction() {
    super.doCancelAction()
  }
}
