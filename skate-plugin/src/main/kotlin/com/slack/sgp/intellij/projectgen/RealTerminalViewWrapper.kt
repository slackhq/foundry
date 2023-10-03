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
package com.slack.sgp.intellij.projectgen

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.TerminalView

/* Wrapper around Jetbrains TerminalView to help simplify testing */
class RealTerminalViewWrapper(private val project: Project) : TerminalViewWrapper {
  override fun executeCommand(command: TerminalCommand) {
    // Create new terminal window to run given command
    TerminalView.getInstance(project)
      .createLocalShellWidget(command.projectPath, command.tabName)
      .executeCommand(command.command)
  }
}
