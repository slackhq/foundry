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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitContent
import com.slack.circuit.runtime.ui.ui
import com.slack.sgp.intellij.tracing.SkateSpanBuilder
import com.slack.sgp.intellij.tracing.SkateTraceReporter
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Paths
import java.time.Instant


class ProjectGenMenuAction
@JvmOverloads
constructor(
  private val offline: Boolean = false
) : AnAction() {

  private val skateSpanBuilder = SkateSpanBuilder()
  private val startTimestamp = Instant.now()

  override fun actionPerformed(e: AnActionEvent) {
    ProjectGenWindow(e.project).show()
  }

  fun sendUsageTrace(project: Project) {
    SkateTraceReporter(project, offline)
      .createPluginUsageTraceAndSendTrace(
        "project_generator",
        startTimestamp,
        skateSpanBuilder.getKeyValueList()
      )
  }

  class ProjectGenWindow(private val currentProject: Project?) : ComposeDialog(currentProject) {
    init {
      title = "Project Generator"
    }

    @Composable
    override fun dialogContent() {

      logger<ProjectGenMenuAction>().info(currentProject?.basePath)
      val rootDir = remember {
        val path = currentProject?.basePath
          ?: FileSystems.getDefault().getPath(".").toAbsolutePath().normalize().toFile().absolutePath
        check(Paths.get(path).toFile().isDirectory) { "Must pass a valid directory" }
        path
      }
      File(rootDir + "/.projectgenlock").createNewFile()

      val circuit = remember {
        Circuit.Builder()
          .addPresenterFactory { _, _, _ -> ProjectGenPresenter(rootDir, ::doOKAction) }
          .addUiFactory { _, _ ->
            ui<ProjectGenScreen.State> { state, modifier -> ProjectGen(state, modifier) }
          }
          .build()
      }
      SlackDesktopTheme() {
        CircuitContent(ProjectGenScreen, circuit = circuit)
      }
    }
  }
}
