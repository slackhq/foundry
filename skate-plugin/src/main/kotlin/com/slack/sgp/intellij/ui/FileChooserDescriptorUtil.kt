package com.slack.sgp.intellij.ui

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.vfs.VirtualFile

object FileChooserDescriptorUtil {

    fun createJarsChooserDescriptor() =
        createMultipleFilesChooserDescriptor(allowJars = true) { it.extension.equals("jar", ignoreCase = true) }

    fun createYamlChooserDescriptor() =
        createMultipleFilesChooserDescriptor {
            it.extension.equals("yml", ignoreCase = true) || it.extension.equals("yaml", ignoreCase = true)
        }

    private fun createMultipleFilesChooserDescriptor(
        allowFiles: Boolean = true,
        allowDirectories: Boolean = false,
        allowJars: Boolean = false,
        showIncompatibleFiles: Boolean = false,
        fileFilter: (VirtualFile) -> Boolean = { true }
    ) =
        object : FileChooserDescriptor(allowFiles, allowDirectories, allowJars, allowJars, false, true) {
            override fun isFileSelectable(file: VirtualFile?) =
                file?.let { fileFilter(file) } ?: false

            override fun isFileVisible(file: VirtualFile?, showHiddenFiles: Boolean) =
                if (file == null || file.isDirectory || showIncompatibleFiles) true else fileFilter(file)
        }

    fun createSingleXmlChooserDescriptor() =
        createSingleFileChooserDescriptor { it.extension.equals("xml", ignoreCase = true) }

    private fun createSingleFileChooserDescriptor(
        allowFiles: Boolean = true,
        allowJars: Boolean = false,
        showIncompatibleFiles: Boolean = false,
        fileFilter: (VirtualFile) -> Boolean = { true }
    ) =
        object : FileChooserDescriptor(allowFiles, false, allowJars, allowJars, false, false) {
            override fun isFileSelectable(file: VirtualFile?) =
                file?.let { fileFilter(file) } ?: false

            override fun isFileVisible(file: VirtualFile?, showHiddenFiles: Boolean) =
                if (file == null || file.isDirectory || showIncompatibleFiles) true else fileFilter(file)
        }
}