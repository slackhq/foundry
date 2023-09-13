/*
 * Copyright (C) 2023 Slack Technologies, LLC
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
package com.slack.sgp.intellij.featureflags

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiUtilBase

/**
 * Listener that responds to file opening events within the IDE. This listener is interested in
 * files that match the pattern `.*Features.kt`. Upon detection of such a file opening, it triggers
 * a PSI analysis and attempts to extract feature flags.
 */
class FeatureFlagFileListener : FileEditorManagerListener {

  override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
    if (isFeatureFile(file)) {
      val editor = source.selectedTextEditor
      val psiFile = PsiUtilBase.getPsiFileInEditor(editor!!, source.project)
      psiFile?.let {
        val featureFlagHandler = FeatureFlagExtractor()
        val flags = featureFlagHandler.extractFeatureFlags(psiFile)
        featureFlagHandler.setFeatureFlagsForPsiFile(psiFile, flags)
      }
    }
  }

  private fun isFeatureFile(file: VirtualFile): Boolean {
    return file.name.endsWith("Feature.kt")
  }
}
