// Copyright (C) 2018 Salesforce, Inc.
// Copyright 2018 The Android Open Source Project
// SPDX-License-Identifier: Apache-2.0
package com.slack.sgp.intellij.sidepanel

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.slack.sgp.intellij.sidepanel.AssistantToolWindowService.Companion.TOOL_WINDOW_TITLE

/**
 * Service for creating and maintaining assistant tool window content.
 *
 * Note the registration of the tool window is done programmatically (not via extension point).
 */
interface AssistantToolWindowService : Disposable {
  /** Opens the assistant window and populate it with the tutorial indicated by [bundleId]. */
  fun openAssistant(bundleId: String, defaultTutorialCardId: String? = null)

  companion object {
    const val TOOL_WINDOW_TITLE = "Assistant"
  }
}

private class AssistantToolWindowServiceImpl(private val project: Project) :
  AssistantToolWindowService {

  private val assistSidePanel: AssistSidePanel by lazy { AssistSidePanel(project) }

  override fun dispose() {}

  override fun openAssistant(bundleId: String, defaultTutorialCardId: String?) {
    val toolWindowManager = ToolWindowManager.getInstance(project)
    var toolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_TITLE)

    if (toolWindow == null) {
      // NOTE: canWorkInDumbMode must be true or the window will close on gradle sync.
      toolWindow =
        toolWindowManager.registerToolWindow(
          TOOL_WINDOW_TITLE,
          false,
          ToolWindowAnchor.RIGHT,
          this,
          true
        )
      //            TODO: Add Icon
      //            toolWindow.setIcon(StudioIcons.Shell.ToolWindows.ASSISTANT)
    }
    toolWindow.helpId = bundleId

    createAssistantContent(bundleId, toolWindow, defaultTutorialCardId)

    // Always active the window, in case it was previously minimized.
    toolWindow.activate(null)
  }

  private fun createAssistantContent(
    bundleId: String,
    toolWindow: ToolWindow,
    defaultTutorialCardId: String?
  ) {
    var content: Content? = null
    //        assistSidePanel.showBundle(bundleId, defaultTutorialCardId) { content?.displayName =
    // it.name }
    val contentFactory = ContentFactory.getInstance()
    content =
      contentFactory.createContent(assistSidePanel.loadingPanel, null, false).also {
        val contentManager = toolWindow.contentManager
        contentManager.removeAllContents(true)
        contentManager.addContent(it)
        contentManager.setSelectedContent(it)
      }
    toolWindow.show(null)
  }
}
