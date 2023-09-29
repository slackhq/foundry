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

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalView
import org.mockito.Mockito
import org.mockito.Mockito.verify

class ProjectGenMenuActionTest : BasePlatformTestCase() {
  override fun setUp() {
    super.setUp()

    // Reset relevant settings.
    val settings = skatePluginSettings()
    settings.isProjectGenMenuActionEnabled = true
    settings.projectGenRunCommand = "echo Hello World"
  }

  private fun skatePluginSettings() = project.service<SkatePluginSettings>()

  fun testCommandLineParam() {
    val action = ProjectGenMenuAction()
    val mockTerminal = Mockito.mock(TerminalView::class.java)
    val mockShellWidget = Mockito.mock(ShellTerminalWidget::class.java)
    Mockito.`when`(mockTerminal.createLocalShellWidget(project.basePath, ProjectGenMenuAction.PROJECT_GEN_TAB_NAME)).thenReturn(mockShellWidget)

    val actionEvent = Mockito.mock(AnActionEvent::class.java)
    Mockito.`when`(actionEvent.project).thenReturn(project)

    action.actionPerformed(actionEvent)
    verify(mockShellWidget).executeCommand("echo Hello World")
  }
}
