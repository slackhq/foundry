package com.slack.sgp.intellij.ui

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.vfs.VirtualFile

object FileChooseDescriptorSkate {
  fun singleMdFileChooserDescriptor() = createSingleFileChooserDescriptor {
    it.extension.equals("md", ignoreCase = true)
  }

  private fun createSingleFileChooserDescriptor(fileFilter: (VirtualFile) -> Boolean) =
    object : FileChooserDescriptor(true, false, false, false, false, false) {
      override fun isFileSelectable(file: VirtualFile?) = file?.let { fileFilter(it) } ?: false
      override fun isFileVisible(file: VirtualFile?, showHiddenFiles: Boolean) =
        if (file == null || file.isDirectory) true else fileFilter(file)
    }
}
