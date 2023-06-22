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
package com.slack.sgp.intellij.ui

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.vfs.VirtualFile

object FileChooserDescriptorUtil {

  fun createJarsChooserDescriptor() =
    createMultipleFilesChooserDescriptor(allowJars = true) {
      it.extension.equals("jar", ignoreCase = true)
    }

  fun createYamlChooserDescriptor() = createMultipleFilesChooserDescriptor {
    it.extension.equals("yml", ignoreCase = true) || it.extension.equals("yaml", ignoreCase = true)
  }

  private fun createMultipleFilesChooserDescriptor(
    allowFiles: Boolean = true,
    allowDirectories: Boolean = false,
    allowJars: Boolean = false,
    showIncompatibleFiles: Boolean = false,
    fileFilter: (VirtualFile) -> Boolean = { true }
  ) =
    object :
      FileChooserDescriptor(allowFiles, allowDirectories, allowJars, allowJars, false, true) {
      override fun isFileSelectable(file: VirtualFile?) = file?.let { fileFilter(file) } ?: false

      override fun isFileVisible(file: VirtualFile?, showHiddenFiles: Boolean) =
        if (file == null || file.isDirectory || showIncompatibleFiles) true else fileFilter(file)
    }

  fun createSingleXmlChooserDescriptor() = createSingleFileChooserDescriptor {
    it.extension.equals("xml", ignoreCase = true)
  }

  private fun createSingleFileChooserDescriptor(
    allowFiles: Boolean = true,
    allowJars: Boolean = false,
    showIncompatibleFiles: Boolean = false,
    fileFilter: (VirtualFile) -> Boolean = { true }
  ) =
    object : FileChooserDescriptor(allowFiles, false, allowJars, allowJars, false, false) {
      override fun isFileSelectable(file: VirtualFile?) = file?.let { fileFilter(file) } ?: false

      override fun isFileVisible(file: VirtualFile?, showHiddenFiles: Boolean) =
        if (file == null || file.isDirectory || showIncompatibleFiles) true else fileFilter(file)
    }
}
