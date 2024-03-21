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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.EditorBasedWidget
import com.slack.sgp.intellij.codeowners.CodeOwnerFileFetcherImpl
import com.slack.sgp.intellij.codeowners.CodeOwnerInfo
import com.slack.sgp.intellij.codeowners.CodeOwnerRepository

const val CODE_OWNER_WIDGET_ID = "CodeOwnerWidget"

/** CodeOwnerWidget is responsible for displaying and handling code owner widget functionality. */
class CodeOwnerWidget(project: Project) :
  EditorBasedWidget(project), StatusBarWidget.MultipleTextValuesPresentation {

  init {
    // Connect to message bus and listen for file editor changes.
    val bus = ApplicationManager.getApplication().messageBus
    val connection = bus.connect(this)
    connection.subscribe(
      FileEditorManagerListener.FILE_EDITOR_MANAGER,
      object : FileEditorManagerListener {
        override fun selectionChanged(event: FileEditorManagerEvent) {
          this@CodeOwnerWidget.selectionChanged(event)
        }
      },
    )
  }

  private var currentSelectedFile: VirtualFile? = null
  private val codeOwnerFileFetcher = CodeOwnerFileFetcherImpl(project)
  private val codeOwnersRepo = CodeOwnerRepository(codeOwnerFileFetcher)
  private val logger = logger<CodeOwnerWidget>()

  override fun ID() = CODE_OWNER_WIDGET_ID

  override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

  override fun selectionChanged(event: FileEditorManagerEvent) {
    // Set the current selected file from the FileEditorManagerEvent.newFile VirtualFile.
    // We don't worry if it's null as that may be expected
    // and will lead to the widget displaying "none" owners.
    currentSelectedFile = event.newFile
    myStatusBar?.updateWidget(ID())
  }

  override fun getTooltipText() = "Click to show in code_ownership yaml"

  override fun getClickConsumer() = null

  override fun getPopupStep(): ListPopup? {
    val owners = getCurrentCodeOwnerInfo()
    return when (owners.size) {
      1 -> {
        // for single owner navigate directly to their codeowner line
        goToOwner(owners.first())
        null
      }
      in 2..Int.MAX_VALUE -> {
        // launch picker popup if more than 1 owner
        JBPopupFactory.getInstance().createListPopup(MultiCodeOwnerPopupStep(owners))
      }
      else -> {
        // no-op, do nothing if there's no owners
        null
      }
    }
  }

  override fun getSelectedValue(): String {
    val owners = getCurrentCodeOwnerInfo()
    val first = owners.firstOrNull()
    val numOthers = owners.size - 1

    // Handle multiple/single/no owners scenarios
    val widgetText =
      when (owners.size) {
        0 -> "Owner: none"
        1 -> "Owner: ${first?.team}"
        2 -> "Owners: ${first?.team} & 1 other"
        in 3..Int.MAX_VALUE -> "Owners: ${first?.team} & $numOthers others"
        else -> "Owners: Not available"
      }
    logger.debug("getSelectedValue computed value: $widgetText")
    return widgetText
  }

  private fun getCurrentCodeOwnerInfo(): List<CodeOwnerInfo> {
    val file = currentSelectedFile
    if (file != null) {
      logger.debug("getCurrentCodeOwnerInfo called w/ path: ${file.path}")
      return codeOwnersRepo.getCodeOwnership(file.path)
    }
    return emptyList()
  }

  private fun goToOwner(ownerInfo: CodeOwnerInfo) {
    val codeOwnersFile = codeOwnerFileFetcher.getCodeOwnershipFile()
    val virtualFile =
      codeOwnersFile?.toPath()?.let { VirtualFileManager.getInstance().findFileByNioPath(it) }
        ?: return
    OpenFileDescriptor(project, virtualFile, ownerInfo.codeOwnerLineNumber, 0).navigate(true)
  }

  inner class MultiCodeOwnerPopupStep(private val owners: List<CodeOwnerInfo>) :
    BaseListPopupStep<String>("Owners", owners.map { it.team }) {
    override fun onChosen(selectedValue: String?, finalChoice: Boolean): PopupStep<*>? {
      val selectedTeam = owners.firstOrNull { it.team == selectedValue }
      if (selectedTeam != null) {
        goToOwner(selectedTeam)
      }
      return super.onChosen(selectedValue, finalChoice)
    }
  }
}
