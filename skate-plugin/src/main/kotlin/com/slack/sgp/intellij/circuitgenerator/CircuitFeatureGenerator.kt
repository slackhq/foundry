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
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.slack.sgp.intellij.util.getJavaPackageName
import java.io.File
import slack.tooling.projectgen.circuitgen.CircuitComponentFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.readText

class CircuitFeatureGenerator : AnAction(), DumbAware {

  override fun actionPerformed(event: AnActionEvent) {
    var featureNameField = ""
    var selectedType = "UI + Presenter"
    var assistedInject = true
    val centerPanel = panel {
      row("Name") {
        textField().bindText({ featureNameField }, { featureNameField = it }).validationOnApply {
          if (it.text.isBlank()) error("Text cannot be empty") else null
        }
      }
      row("Template") {
        comboBox(items = listOf("UI + Presenter", "Presenter Only"))
          .bindItem(
            { selectedType },
            {
              if (it != null) {
                selectedType = it
              }
            },
          )
      }
      row {
        checkBox("Enable Assisted Injection")
          .bindSelected(
            getter = { assistedInject },
            setter = { assistedInject = it }
          )
      }
    }
    val dialog = dialog(title = "New Circuit Feature", panel = centerPanel)
    if (dialog.showAndGet()) {
      event.getData(CommonDataKeys.VIRTUAL_FILE)?.let { data ->
        createCircuitFeature(featureNameField, selectedType, assistedInject, data, event.project)
      }
    }
  }

  private fun createCircuitFeature(
    featureNameField: String,
    selectedType: String,
    assistedInject: Boolean,
    data: VirtualFile,
    project: Project?,
  ) {
    val directory = if (data.isDirectory) data.path else data.parent.path
    val packageName = directory.getJavaPackageName()
    when (selectedType) {
      "UI + Presenter" ->
        CircuitComponentFactory()
          .generateCircuitAndComposeUI(directory, packageName, featureNameField, assistedInject)
      "Presenter Only" ->
        CircuitComponentFactory().generateCircuitPresenter(directory, packageName, featureNameField, assistedInject)
    }

    project?.let {
      val circuitScreenPath = File("${directory}/${featureNameField}Screen.kt")
      logger<CircuitFeatureGenerator>().info(circuitScreenPath.path)
      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(circuitScreenPath)?.let { file ->
        FileEditorManager.getInstance(it).openFile(file)
      }
      LocalFileSystem.getInstance().refresh(true)
      addMissingGradleDependency(project.basePath, directory)
    }
  }
  private fun findNearestProjectDirRecursive(
    repoRoot: Path,
    currentDir: Path?,
    cache: MutableMap<Path, Path?>,
  ): Path? {
    if (currentDir == null || currentDir == repoRoot) {
      error("Could not find build.gradle(.kts)")
    }

    return cache.getOrPut(currentDir) {
      val hasBuildFile =
        currentDir.resolve("build.gradle.kts").exists() ||
          currentDir.resolve("build.gradle").exists()
      if (hasBuildFile) {
        if (currentDir.resolve("build.gradle.kts").exists()) {
          return currentDir.resolve("build.gradle.kts")
        } else {
          return currentDir.resolve("build.gradle")
        }
      }
      findNearestProjectDirRecursive(repoRoot, currentDir.parent, cache)
    }
  }
  fun addMissingGradleDependency(repoRoot: String?, directory: String) {
    logger<CircuitFeatureGenerator>().info("In hereeee")
    logger<CircuitFeatureGenerator>().info(repoRoot)
    if (repoRoot.isNullOrBlank()) return
    val gradlePath = findNearestProjectDirRecursive(Paths.get(repoRoot), Paths.get(directory), mutableMapOf())
    logger<CircuitFeatureGenerator>().info(gradlePath?.pathString)
    var buildFile = gradlePath?.readText()
    val pluginBlockPattern = Regex("plugins\\s*\\{([\\s\\S]*?)\\}", RegexOption.DOT_MATCHES_ALL)
    val slackFeatureBlock = Regex("slack\\s*\\{\\s*feature\\s*\\{\\s*(.*?)\\s*\\}\\s*(?:[^}]*\\})?", RegexOption.DOT_MATCHES_ALL)
    val parcelizeImport = "  alias(libs.plugins.kotlin.plugin.parcelize)\n"
    val ciruitImport = "circuit()"
    logger<CircuitFeatureGenerator>().info("LINHHHH")
    logger<CircuitFeatureGenerator>().info(buildFile)
    if (buildFile != null) {
      if (!buildFile.contains(parcelizeImport)) {
        val pluginBlockMatch = pluginBlockPattern.find(buildFile)?.groups?.get(1)?.value ?: ""
        logger<CircuitFeatureGenerator>().info(pluginBlockMatch)
        val addedPluginBlock = "${pluginBlockMatch}${parcelizeImport}"
        logger<CircuitFeatureGenerator>().info("inside to replace parcelize")
        buildFile = pluginBlockMatch.let { buildFile!!.replace(it, addedPluginBlock) }
        }

      if (!buildFile.contains(ciruitImport)) {
          val slackFeatureBlockContent = slackFeatureBlock.find(buildFile)?.groups?.get(1)?.value ?: ""
          logger<CircuitFeatureGenerator>().info(slackFeatureBlockContent)
          if (slackFeatureBlockContent == "") {
            buildFile += """
                slack {
                  features { circuit() }
                 }
              """.trimIndent()
          } else {
            val addedCircuit = "$slackFeatureBlockContent\n    circuit()\n"
            buildFile = buildFile.replace(slackFeatureBlockContent, addedCircuit)
          }
          logger<CircuitFeatureGenerator>().info("inside to replace circuit")
        }
      Files.writeString(gradlePath, buildFile)

    }
  }
}
