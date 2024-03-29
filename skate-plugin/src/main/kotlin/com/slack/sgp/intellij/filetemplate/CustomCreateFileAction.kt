package com.slack.sgp.intellij.filetemplate

import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.FileTemplateUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.slack.sgp.intellij.filetemplate.model.FileTemplateFactory
import java.io.File
import java.io.FileNotFoundException
import javax.swing.Icon

abstract class CustomCreateFileAction(title: String, desc: String, icon: Icon) : CreateFileFromTemplateAction(title, desc, icon) {

  public override fun createFileFromTemplate(name: String, template: FileTemplate, dir: PsiDirectory): PsiFile? {
    Thread.currentThread().contextClassLoader = this.javaClass.classLoader
    val templateStream = Thread.currentThread().contextClassLoader.getResourceAsStream("fileTemplateSettings.yaml")
      ?: throw FileNotFoundException("File template settings file not found")
    val templateSettings = FileTemplateFactory(templateStream).getTemplates()
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
}