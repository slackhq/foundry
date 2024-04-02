package com.slack.sgp.intellij.filetemplate

import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import org.jetbrains.kotlin.idea.KotlinFileType

class CreateCircuitFeature : CustomCreateFileAction( "New Circuit Feature", "Creates new Circuit Feature", KotlinFileType.INSTANCE.icon), DumbAware {
  override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
    builder.setTitle("New Circuit Feature Name")
      .addKind("Presenter + Compose UI", KotlinFileType.INSTANCE.icon, "Circuit Presenter and Compose UI")
      .addKind("Presenter only", KotlinFileType.INSTANCE.icon, "Circuit Presenter (without UI)")
  }

  override fun getActionName(directory: PsiDirectory, newName: String, templateName: String) = "New Circuit feature"
}

