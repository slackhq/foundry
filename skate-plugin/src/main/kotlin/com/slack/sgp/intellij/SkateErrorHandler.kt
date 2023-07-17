/*
 * Copyright (C) 2016 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.slack.sgp.intellij

import com.bugsnag.Bugsnag
import com.bugsnag.Severity
import com.intellij.diagnostic.AbstractMessage
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.ErrorReportSubmitter
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.util.Consumer
import java.awt.Component

/**
 * Using SqlDelightErrorHandler to use for crash reporting for the Skate plugin. Currently just have
 * placeholders for GIT_SHA, VERSION, and BUGSNAG_KEY.
 */
// Adapted from
// https://github.com/cashapp/sqldelight/blob/5512326251b1e9f91ddef49dda75d27405943e2f/sqldelight-idea-plugin/src/main/kotlin/app/cash/sqldelight/intellij/SqlDelightErrorHandler.kt#L35
class SkateErrorHandler : ErrorReportSubmitter() {
  val skateBugsnagApiKey = "BUGSNAG_KEY_PLACEHOLDER"

  val skateBugsnag = Bugsnag(skateBugsnagApiKey, false)

  init {
    skateBugsnag.setAutoCaptureSessions(false)
    skateBugsnag.startSession()
    skateBugsnag.setAppVersion("VERSION_PLACEHOLDER")
    skateBugsnag.setProjectPackages("com.slack.sgp.intellij")
    skateBugsnag.addCallback {
      it.addToTab("Device", "osVersion", System.getProperty("os.version"))
      it.addToTab("Device", "JRE", System.getProperty("java.version"))
      it.addToTab("Device", "IDE Version", ApplicationInfo.getInstance().fullVersion)
      it.addToTab("Device", "IDE Build #", ApplicationInfo.getInstance().build)
      it.addToTab("Device", "Plugin SHA", "GIT_SHA_PLACEHOLDER")
      PluginManagerCore.getPlugins().forEach { plugin ->
        it.addToTab("Plugins", plugin.name, "${plugin.pluginId} : ${plugin.version}")
      }
    }
  }

  override fun getReportActionText() = "Send to Skate"

  override fun submit(
    events: Array<out IdeaLoggingEvent>,
    additionalInfo: String?,
    parentComponent: Component,
    consumer: Consumer<in SubmittedReportInfo>
  ): Boolean {
    for (event in events) {
      if (skateBugsnagApiKey.isNotBlank()) {
        val throwable = (event.data as? AbstractMessage)?.throwable ?: event.throwable
        skateBugsnag.notify(throwable, Severity.ERROR) {
          it.addToTab("Data", "message", event.message)
          it.addToTab("Data", "additional info", additionalInfo)
          it.addToTab("Data", "stacktrace", event.throwableText)
        }
      }
    }
    return true
  }
}
