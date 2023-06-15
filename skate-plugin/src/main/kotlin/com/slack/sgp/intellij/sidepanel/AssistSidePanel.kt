// Copyright (C) 2018 Salesforce, Inc.
// Copyright 2018 The Android Open Source Project
// SPDX-License-Identifier: Apache-2.0
package com.slack.sgp.intellij.sidepanel

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBLoadingPanel
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JPanel

/** Panel for "assistant" flows such as tutorials, domain specific tools, etc. */
//  File link:
// https://cs.android.com/android-studio/platform/tools/adt/idea/+/mirror-goog-studio-main:assistant/src/com/android/tools/idea/assistant/AssistSidePanel.kt
class AssistSidePanel(private val project: Project) : JPanel(BorderLayout()) {
  val loadingPanel: JBLoadingPanel
  private val errorPanel: JPanel
  private val errorText: JBLabel

  private val log: Logger
    get() = Logger.getInstance(AssistSidePanel::class.java)

  //    private var featuresPanel: FeaturesPanel? = null

  init {
    border = BorderFactory.createEmptyBorder()
    isOpaque = false

    loadingPanel = JBLoadingPanel(BorderLayout(), project, 200)
    loadingPanel.add(this, BorderLayout.CENTER)
    loadingPanel.setLoadingText("Loading assistant content")
    loadingPanel.name = "assistantPanel"

    // Add an error message to show when there is an error while loading
    errorPanel = JPanel(BorderLayout())
    val message = "Error loading assistant panel. Please check idea.log for detailed error message."
    val htmlText =
      "<html><div style='text-align: center;'>${StringUtil.escapeXmlEntities(message)}</div></html>"
    errorText = JBLabel(htmlText)
    errorText.horizontalAlignment = JBLabel.CENTER
    errorPanel.add(errorText, BorderLayout.CENTER)
    this.add(errorPanel, BorderLayout.CENTER)
    errorPanel.isVisible = false
  }

  //  fun showBundle(
  //    bundleId: String,
  //    defaultTutorialId: String? = null,
  //    //        onBundleCreated: ((TutorialBundleData) -> Unit)? = null
  //    ) {
  //    //        featuresPanel?.let { remove(it) }
  //    //        loadingPanel.startLoading()
  //    //        errorPanel.isVisible = false
  //    //
  //    //        val bundleCreator =
  //    //            try {
  //    //                AssistantBundleCreator.EP_NAME.extensions.first { it.bundleId == bundleId
  // }
  //    //            } catch (e: NoSuchElementException) {
  //    //                log.warn("Unable to find configuration for the selected action:
  // $bundleId")
  //    //                return
  //    //            }
  //    //
  //    //        // Instantiate the bundle from a configuration file using the default bundle
  // mapping.
  //    //        // If null, creator must provide the bundle instance themselves.
  //    //        val config =
  //    //            try {
  //    //                bundleCreator.config
  //    //            } catch (e: FileNotFoundException) {
  //    //                log.warn(e)
  //    //                null
  //    //            }
  //    //
  //    //        // Config provided, use that with the default bundle.
  //    //        if (config != null) {
  //    //            AssistantGetBundleFromConfigTask(
  //    //                project,
  //    //                config,
  //    //                AssistantLoadingCallback(bundleId, bundleCreator, defaultTutorialId,
  //    // onBundleCreated),
  //    //                bundleCreator.bundleId
  //    //            )
  //    //                .queue()
  //    //        } else {
  //    //            AssistantGetBundleTask(
  //    //                project,
  //    //                bundleCreator,
  //    //                AssistantLoadingCallback(bundleId, bundleCreator, defaultTutorialId,
  //    // onBundleCreated)
  //    //            )
  //    //                .queue()
  //    //        }
  //  }
}
