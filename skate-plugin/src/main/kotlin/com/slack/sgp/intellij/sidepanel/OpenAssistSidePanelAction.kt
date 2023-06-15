//// Copyright (C) 2018 Salesforce, Inc.
//// Copyright 2018 The Android Open Source Project
//// SPDX-License-Identifier: Apache-2.0
// package com.slack.sgp.intellij.sidepanel
//
// import com.intellij.openapi.actionSystem.ActionManager
// import com.intellij.openapi.actionSystem.AnAction
// import com.intellij.openapi.actionSystem.AnActionEvent
// import com.intellij.openapi.application.ApplicationManager
// import com.intellij.openapi.project.Project
//
/// ** Triggers the creation of the Developer Services side panel. */
// class OpenAssistSidePanelAction : AnAction() {
//  override fun actionPerformed(event: AnActionEvent) {
//    val thisProject = checkNotNull(event.project)
//    val actionId = ActionManager.getInstance().getId(this)
//    openWindow(actionId, thisProject)
//  }
//
//  /** Opens the assistant associated with the given actionId at the end of event thread */
//  fun openWindow(actionId: String, project: Project) {
//    ApplicationManager.getApplication().invokeLater {
//      project.getService(AssistantToolWindowService::class.java).openAssistant(actionId, null)
//    }
//  }
// }
