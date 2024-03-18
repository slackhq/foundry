/*
 * Copyright (C) 2024 Slack Technologies, LLC
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
package com.slack.sgp.intellij.idemetrics

import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.intellij.openapi.project.Project
import com.slack.sgp.intellij.tracing.SkateSpanBuilder
import com.slack.sgp.intellij.tracing.SkateTracingEvent
import com.slack.sgp.intellij.util.getTraceReporter
import com.slack.sgp.intellij.util.isTracingEnabled
import com.slack.sgp.tracing.helper.UUIDGenerator
import java.time.Instant
import okio.ByteString

class GradleSyncSubscriber : GradleSyncListener {

  private var parentId: ByteString = ByteString.EMPTY

  override fun syncStarted(project: Project) {
    val startTimestamp = Instant.now()
    super.syncStarted(project)
    if (!project.isTracingEnabled()) return
    parentId = UUIDGenerator().generateUUIDWith32Characters()
    sendTrace(project, startTimestamp, SkateTracingEvent.GradleSync.GRADLE_SYNC_STARTED)
  }

  override fun syncSucceeded(project: Project) {
    val startTimestamp = Instant.now()
    super.syncSucceeded(project)
    if (!project.isTracingEnabled()) return
    sendTrace(project, startTimestamp, SkateTracingEvent.GradleSync.GRADLE_SYNC_SUCCEDDED)
  }

  fun sendTrace(project: Project, startTimestamp: Instant, event: SkateTracingEvent) {
    val skateSpanBuilder = SkateSpanBuilder()
    skateSpanBuilder.addTag("event", event)
    project
      .getTraceReporter()
      .createPluginUsageTraceAndSendTrace(
        "gradle_sync",
        startTimestamp,
        skateSpanBuilder.getKeyValueList(),
        parentId,
      )
  }
}
