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
package foundry.intellij.skate.idemetrics

import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.intellij.openapi.project.Project
import foundry.intellij.skate.tracing.SkateSpanBuilder
import foundry.intellij.skate.tracing.SkateTracingEvent
import foundry.intellij.skate.util.getTraceReporter
import foundry.intellij.skate.util.isTracingEnabled
import foundry.tracing.model.makeId
import java.time.Instant
import okio.ByteString

class GradleSyncSubscriber : GradleSyncListener {

  private var parentId: ByteString = ByteString.EMPTY

  override fun syncStarted(project: Project) {
    super.syncStarted(project)
    if (!project.isTracingEnabled()) return
    parentId = makeId()
    sendTrace(
      project,
      Instant.now(),
      SkateTracingEvent.GradleSync.GRADLE_SYNC_STARTED,
      spanId = parentId,
      parentId = ByteString.EMPTY,
    )
  }

  override fun syncSkipped(project: Project) {
    super.syncSkipped(project)
    if (!project.isTracingEnabled()) return
    sendTrace(
      project,
      Instant.now(),
      SkateTracingEvent.GradleSync.GRADLE_SYNC_SKIPPED,
      spanId = makeId(),
      parentId = parentId,
    )
  }

  override fun syncSucceeded(project: Project) {
    super.syncSucceeded(project)
    if (!project.isTracingEnabled()) return
    sendTrace(
      project,
      Instant.now(),
      SkateTracingEvent.GradleSync.GRADLE_SYNC_SUCCEDDED,
      spanId = makeId(),
      parentId = parentId,
    )
  }

  fun sendTrace(
    project: Project,
    startTimestamp: Instant,
    event: SkateTracingEvent,
    spanId: ByteString,
    parentId: ByteString,
  ) {
    val skateSpanBuilder = SkateSpanBuilder()
    skateSpanBuilder.addTag("event", event)
    project
      .getTraceReporter()
      .createPluginUsageTraceAndSendTrace(
        "gradle_sync",
        startTimestamp,
        skateSpanBuilder.getKeyValueList(),
        spanId,
        parentId,
      )
  }
}
