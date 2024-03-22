package com.slack.sgp.intellij.filetemplate

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.decodeFromStream
import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.FileTemplateUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.slack.sgp.intellij.filetemplate.model.FileTemplateSettings
import com.slack.sgp.intellij.filetemplate.model.TemplateSetting
import org.jetbrains.kotlin.idea.KotlinFileType
import java.io.File
import java.io.FileNotFoundException

class CreateCircuitFeature : CreateFileFromTemplateAction( "Circuit Feature", "Creates new Circuit Feature", KotlinFileType.INSTANCE.icon), DumbAware {
  override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
    builder.setTitle("New Circuit Feature Name")
      .addKind("Presenter + Compose UI", KotlinFileType.INSTANCE.icon, "Circuit Presenter + Compose UI")
      .addKind("Presenter only", KotlinFileType.INSTANCE.icon, "Circuit Presenter (without UI)")
      .addKind("UdfViewModel conversion", KotlinFileType.INSTANCE.icon, "UdfViewModel convert")
  }

  override fun getActionName(directory: PsiDirectory, newName: String, templateName: String) = "Circuit feature"

  override fun createFileFromTemplate(name: String, template: FileTemplate, dir: PsiDirectory): PsiFile? {
    val templateSettings = fileTemplateSettingParser()
    val allTemplate = FileTemplateManager.getInstance(dir.project).allJ2eeTemplates

    allTemplate.forEach { child ->
      if (child.name.contains(template.name)) {
        val properties = FileTemplateManager.getInstance(dir.project).defaultProperties
        properties.setProperty("NAME", name)
        val suffix = templateSettings[child.name]!!.fileNameSuffix

        // Create test directory
        if (suffix.contains("Test") && !dir.virtualFile.path.contains("test")) {
          val testPath = File(dir.virtualFile.path.replace("src/main", "src/test"))
          testPath.mkdirs()
          val testFolder = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(testPath)
          val psiDir = testFolder?.let { PsiManager.getInstance(dir.project).findDirectory(it) } as PsiDirectory
          FileTemplateUtil.createFromTemplate(child, name + suffix, properties, psiDir)
        } else {
          FileTemplateUtil.createFromTemplate(child, name + suffix, properties, dir)
        }
      }
    }
      return null
  }

  private fun fileTemplateSettingParser() : Map<String, TemplateSetting> {
    Thread.currentThread().contextClassLoader = this.javaClass.classLoader
    val template = Thread.currentThread().contextClassLoader.getResourceAsStream("fileTemplateSettings")
      ?: throw FileNotFoundException("File template settings file not found")
    val table = Yaml(configuration = YamlConfiguration(strictMode = false)).decodeFromStream<FileTemplateSettings>(template)
    return table.templates.associateBy { it.name }
  }
}