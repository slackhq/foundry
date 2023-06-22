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

import com.intellij.CommonBundle
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.DialogTitle
import com.intellij.openapi.util.NlsContexts.Label
import com.intellij.openapi.vcs.changes.ui.VirtualFileListCellRenderer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import javax.swing.DefaultListModel
import javax.swing.JPanel
import javax.swing.ListSelectionModel

@Suppress("UnstableApiUsage") // For NlsContexts annotations
internal class FilesListPanel(
  private val listModel: ListModel,
  private val project: Project,
  @DialogTitle private val fileChooserTitle: String,
  @Label private val fileChooserDescription: String,
  private val descriptorProvider: () -> FileChooserDescriptor
) {

  private val list =
    JBList(listModel).apply {
      dragEnabled = true
      selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
      cellRenderer = VirtualFileListCellRenderer(project)
    }

  fun decorated(): JPanel =
    ToolbarDecorator.createDecorator(list)
      .setAddAction { onAddFileClick(listModel) }
      .setRemoveAction { onRemoveFileClick(listModel) }
      .setButtonComparator(
        CommonBundle.message("button.add"),
        CommonBundle.message("button.remove")
      )
      .createPanel()

  private fun onRemoveFileClick(listModel: ListModel) {
    listModel.removeAt(list.selectedIndices.toList())
  }

  private fun onAddFileClick(listModel: ListModel) {
    val descriptor = descriptorProvider()
    descriptor.title = fileChooserTitle
    descriptor.description = fileChooserDescription

    val files = FileChooser.chooseFiles(descriptor, list, project, null)
    for (file in files) {
      if (file != null && !listModel.items.contains(file)) {
        listModel += file
      }
    }
  }

  @Suppress("TooManyFunctions") // Required functionality
  class ListModel(initialItems: List<VirtualFile> = emptyList()) : DefaultListModel<VirtualFile>() {

    val items: List<VirtualFile>
      get() = super.elements().toList()

    init {
      addAll(initialItems)
    }

    operator fun plusAssign(newItem: VirtualFile) {
      addElement(newItem)
    }

    operator fun plusAssign(newItems: Collection<VirtualFile>) {
      addAll(newItems)
    }

    fun removeAt(indices: Collection<Int>) {
      if (indices.isEmpty()) return
      indices.reversed().forEach { indexToRemove -> remove(indexToRemove) }
    }
  }
}
