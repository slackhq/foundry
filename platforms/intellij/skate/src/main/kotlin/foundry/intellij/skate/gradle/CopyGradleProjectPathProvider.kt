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

import com.intellij.ide.actions.DumbAwareCopyPathProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.VirtualFile

/**
 * Action to copy the Gradle project path in the format ":path:to:project". If the selected file is
 * not a Gradle project directory, it will find the nearest parent Gradle project.
 */
class CopyGradleProjectPathProvider : DumbAwareCopyPathProvider() {

  override fun getPathToElement(
    project: Project,
    virtualFile: VirtualFile?,
    editor: Editor?,
  ): String? {
    if (virtualFile == null) return null

    val vcsRoot = ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(virtualFile)
    if (vcsRoot == null) return null

    // Find the nearest Gradle project directory
    val gradleProjectDir =
      GradleProjectUtils.findNearestGradleProject(vcsRoot.path, virtualFile) ?: return null

    // Get the Gradle project path
    return GradleProjectUtils.getGradleProjectPath(project, gradleProjectDir)
  }
}
