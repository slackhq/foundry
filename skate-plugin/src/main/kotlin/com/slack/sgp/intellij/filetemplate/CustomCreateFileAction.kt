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
package com.slack.sgp.intellij.filetemplate

import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.FileTemplateUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.slack.sgp.intellij.filetemplate.model.SettingsParser
import com.slack.sgp.intellij.filetemplate.model.TemplateSetting
import java.io.File
import java.io.FileNotFoundException
import java.util.Properties
import javax.swing.Icon

abstract class CustomCreateFileAction(title: String, desc: String, icon: Icon) :
  CreateFileFromTemplateAction(title, desc, icon) {

  public override fun createFileFromTemplate(
    name: String,
    template: FileTemplate,
    dir: PsiDirectory,
  ): PsiFile? {
    val templateSettings = loadTemplateSettings()
    val properties =
      FileTemplateManager.getInstance(dir.project).defaultProperties.apply {
        setProperty("NAME", name)
      }
    val templates = listOf(template) + getTemplateChildren(dir.project, template)

    val createdFiles = mutableListOf<PsiFile?>()
    templates.forEach { fileTemplate ->
      createdFiles.add(createFileForTemplate(fileTemplate, name, dir, properties, templateSettings))
    }
    return createdFiles.firstOrNull()
  }

  private fun createFileForTemplate(
    template: FileTemplate,
    name: String,
    dir: PsiDirectory,
    properties: Properties,
    templateSettings: Map<String, TemplateSetting>,
  ): PsiFile? {
    val suffix = templateSettings[template.name]?.fileNameSuffix ?: ""
    val targetDir = getTargetDirectory(dir, suffix)
    return FileTemplateUtil.createFromTemplate(template, name + suffix, properties, targetDir)
      .containingFile
  }

  private fun getTemplateChildren(project: Project, template: FileTemplate): List<FileTemplate> {
    val allTemplate = FileTemplateManager.getInstance(project).allJ2eeTemplates
    val filtered =
      allTemplate.filter { it.name.contains("child") && it.name.contains(template.name) }
    return filtered
  }

  private fun loadTemplateSettings(): Map<String, TemplateSetting> {
    val stream =
      this.javaClass.classLoader.getResourceAsStream("fileTemplates/fileTemplateSettings.yaml")
        ?: throw FileNotFoundException("File template settings file not found")
    return SettingsParser(stream).getTemplates()
  }

  private fun getTargetDirectory(dir: PsiDirectory, suffix: String): PsiDirectory {
    if (suffix.contains("Test") && !dir.virtualFile.path.contains("test")) {
      val testPath = File(dir.virtualFile.path.replace("src/main", "src/test"))
      testPath.mkdirs()
      val testFolder = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(testPath)
      return testFolder?.let { PsiManager.getInstance(dir.project).findDirectory(it) }
        as PsiDirectory
    }
    return dir
  }
}
