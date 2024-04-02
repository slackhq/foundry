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

import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import org.jetbrains.kotlin.idea.KotlinFileType

class UdfViewModelMigrate :
  CustomCreateFileAction(
    "UDF ViewModel Convert",
    "Convert to UDF ViewModel",
    KotlinFileType.INSTANCE.icon,
  ),
  DumbAware {
  override fun buildDialog(
    project: Project,
    directory: PsiDirectory,
    builder: CreateFileFromTemplateDialog.Builder,
  ) {
    builder
      .setTitle("UDF ViewModel Convert")
      .addKind("UdfViewModel conversion", KotlinFileType.INSTANCE.icon, "UdfViewModel convert")
  }

  override fun getActionName(dir: PsiDirectory?, newName: String, templateName: String?): String =
    "UDF ViewModel Convert"
}
