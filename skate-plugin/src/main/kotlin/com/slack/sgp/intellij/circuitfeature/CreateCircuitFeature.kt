package com.slack.sgp.intellij.circuitfeature

import com.intellij.codeInsight.template.TemplateManager
import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.actions.AttributesDefaults
import com.intellij.ide.fileTemplates.ui.CreateFromTemplateDialog
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.gradleTooling.get
import java.util.Properties

class CreateCircuitFeature : CreateFileFromTemplateAction( "Circuit Feature", "Creates new Circuit Feature", KotlinFileType.INSTANCE.icon), DumbAware {
  override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
    builder.setTitle("New Circuit Feature")
      .addKind("Presenter + Compose UI", KotlinFileType.INSTANCE.icon, "Circuit feature (Presenter + Compose UI)")
      .addKind("Presenter only", KotlinFileType.INSTANCE.icon, "Circuit feature (no UI)")
      .addKind("UdfViewModel conversion", KotlinFileType.INSTANCE.icon, "UdfViewModel conversion")
  }

  override fun getActionName(directory: PsiDirectory, newName: String, templateName: String) = "Circuit feature"

  override fun createFileFromTemplate(name: String, template: FileTemplate, dir: PsiDirectory): PsiFile? {
    val allTemplate = FileTemplateManager.getInstance(dir.project).allJ2eeTemplates
    allTemplate.forEach { child ->
      logger<CreateCircuitFeature>().info("INSIDE")
      logger<CreateCircuitFeature>().info(template.children.size.toString())
      logger<CreateCircuitFeature>().info(child.name)
      logger<CreateCircuitFeature>().info(template.name)
      logger<CreateCircuitFeature>().info(child.name.contains(template.name).toString())
      if (child.name.contains(template.name)) {
        super.createFileFromTemplate(child.name, child, dir)
      }
    }
      return null
  }
}