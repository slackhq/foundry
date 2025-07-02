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

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.util.IncorrectOperationException
import java.io.File

/**
 * Reference implementation for project paths in project(...) calls. Allows navigation to the
 * referenced project's build file.
 */
class GradleProjectReference(
  private val element: PsiElement,
  private val range: TextRange,
  private val projectPath: String,
) : PsiReference {

  override fun getElement(): PsiElement = element

  override fun getRangeInElement(): TextRange = range

  override fun resolve(): PsiElement? {
    val project = element.project
    val projectPathService = project.getService(ProjectPathService::class.java)

    if (!projectPathService.isValidProjectPath(projectPath)) {
      return null
    }

    // Convert a project path to a filesystem path
    val projectBasePath = project.basePath ?: return null
    val relativePath = projectPath.removePrefix(":").replace(":", "/")
    val projectDir = File(projectBasePath, relativePath)

    if (!projectDir.exists() || !projectDir.isDirectory) {
      return null
    }

    // Look for build.gradle.kts first, then build.gradle
    val buildFileKts = File(projectDir, "build.gradle.kts")
    val buildFile = File(projectDir, "build.gradle")

    val targetFile =
      when {
        buildFileKts.exists() -> buildFileKts
        buildFile.exists() -> buildFile
        else -> return null
      }

    val virtualFile =
      VirtualFileManager.getInstance().findFileByUrl("file://${targetFile.absolutePath}")
        ?: return null

    return PsiManager.getInstance(project).findFile(virtualFile)
  }

  override fun getCanonicalText(): String = projectPath

  override fun handleElementRename(newElementName: String): PsiElement {
    throw IncorrectOperationException("Cannot rename project path reference")
  }

  override fun bindToElement(element: PsiElement): PsiElement {
    throw IncorrectOperationException("Cannot bind project path reference")
  }

  override fun isReferenceTo(element: PsiElement): Boolean {
    val resolved = resolve()
    return resolved != null && resolved == element
  }

  override fun isSoft(): Boolean = false

  /** Navigate to the referenced project's build file. */
  fun navigate() {
    val resolved = resolve()
    if (resolved != null) {
      val virtualFile = resolved.containingFile?.virtualFile
      if (virtualFile != null) {
        FileEditorManager.getInstance(element.project).openFile(virtualFile, true)
      }
    }
  }
}
