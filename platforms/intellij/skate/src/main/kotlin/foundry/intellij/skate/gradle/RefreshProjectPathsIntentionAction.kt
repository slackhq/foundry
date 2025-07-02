/*
 * Copyright (C) 2025 Slack Technologies, LLC
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
package foundry.intellij.skate.gradle

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/** Intention action to refresh project paths from the all-projects.txt file. */
class RefreshProjectPathsIntentionAction : IntentionAction, PriorityAction {

  override fun getText(): String = "Refresh project paths"

  override fun getFamilyName(): String = "Gradle project paths"

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    return file?.name == "build.gradle" || file?.name == "build.gradle.kts"
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    val projectPathService = project.getService(ProjectPathService::class.java)
    projectPathService.invalidateCache()
  }

  override fun startInWriteAction(): Boolean = false

  override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH
}
