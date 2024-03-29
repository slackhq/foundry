package com.slack.sgp.intellij.filetemplate

import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import org.jetbrains.kotlin.idea.KotlinFileType

class UdfViewModelMigrate : CustomCreateFileAction( "UDF ViewModel Convert", "Convert to UDF ViewModel", KotlinFileType.INSTANCE.icon) ,
  DumbAware {
  override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
    builder.setTitle("UDF ViewModel Convert")
      .addKind("UdfViewModel conversion", KotlinFileType.INSTANCE.icon, "UdfViewModel convert")
  }

  override fun getActionName(p0: PsiDirectory?, p1: String, p2: String?): String = "UDF ViewModel Convert"
}