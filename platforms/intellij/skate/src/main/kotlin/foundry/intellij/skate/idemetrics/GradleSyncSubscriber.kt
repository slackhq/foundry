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
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import foundry.intellij.skate.tracing.SkateSpanBuilder
import foundry.intellij.skate.tracing.SkateTracingEvent
import foundry.intellij.skate.util.getTraceReporter
import foundry.intellij.skate.util.isTracingEnabled
import foundry.tracing.model.makeId
import java.time.Instant
import okio.ByteString

class GradleSyncSubscriber : GradleSyncListener {

  private val logger = Logger.getInstance(GradleSyncSubscriber::class.java)
  private var parentId: ByteString = ByteString.EMPTY
  private var syncStartTime: Instant? = null

  override fun syncStarted(project: Project) {
    super.syncStarted(project)
    logger.info("Skate: syncStarted() called for project: ${project.name}")
    val tracingEnabled = project.isTracingEnabled()
    logger.info("Skate: Tracing enabled: $tracingEnabled")
    if (!tracingEnabled) {
      logger.info("Skate: Skipping GRADLE_SYNC_STARTED trace - tracing disabled")
      return
    }
    syncStartTime = Instant.now()
    parentId = makeId()
    logger.info("Skate: Sending GRADLE_SYNC_STARTED trace with parentId: $parentId")
    val spanBuilder = SkateSpanBuilder().apply {
      addTag("event", SkateTracingEvent.GradleSync.GRADLE_SYNC_STARTED)
    }
    sendTrace(
      project,
      syncStartTime!!,
      spanBuilder,
      spanId = parentId,
      parentId = ByteString.EMPTY,
    )
    logger.info("Skate: GRADLE_SYNC_STARTED trace sent successfully")
  }

  override fun syncSkipped(project: Project) {
    super.syncSkipped(project)
    logger.info("Skate: syncSkipped() called for project: ${project.name}")
    val tracingEnabled = project.isTracingEnabled()
    logger.info("Skate: Tracing enabled: $tracingEnabled")
    if (!tracingEnabled) {
      logger.info("Skate: Skipping GRADLE_SYNC_SKIPPED trace - tracing disabled")
      return
    }
    val startTime = syncStartTime ?: Instant.now()
    val endTime = Instant.now()
    val durationMs = java.time.Duration.between(startTime, endTime).toMillis()

    logger.info("Skate: Sending GRADLE_SYNC_SKIPPED trace (duration: ${durationMs}ms)")
    val spanBuilder = SkateSpanBuilder().apply {
      addTag("event", SkateTracingEvent.GradleSync.GRADLE_SYNC_SKIPPED)
      addTag("duration_ms", durationMs)
      addTag("success", true)
    }
    sendTrace(
      project,
      startTime,
      spanBuilder,
      spanId = makeId(),
      parentId = parentId,
    )
    logger.info("Skate: GRADLE_SYNC_SKIPPED trace sent successfully")
    resetState()
  }

  override fun syncSucceeded(project: Project) {
    super.syncSucceeded(project)
    logger.info("Skate: syncSucceeded() called for project: ${project.name}")
    val tracingEnabled = project.isTracingEnabled()
    logger.info("Skate: Tracing enabled: $tracingEnabled")
    if (!tracingEnabled) {
      logger.info("Skate: Skipping GRADLE_SYNC_SUCCEEDED trace - tracing disabled")
      return
    }
    val startTime = syncStartTime ?: Instant.now()
    val endTime = Instant.now()
    val durationMs = java.time.Duration.between(startTime, endTime).toMillis()

    logger.info("Skate: Sending GRADLE_SYNC_SUCCEEDED trace (duration: ${durationMs}ms)")
    val spanBuilder = SkateSpanBuilder().apply {
      addTag("event", SkateTracingEvent.GradleSync.GRADLE_SYNC_SUCCEDDED)
      addTag("duration_ms", durationMs)
      addTag("success", true)
    }
    sendTrace(
      project,
      startTime,
      spanBuilder,
      spanId = makeId(),
      parentId = parentId,
    )
    logger.info("Skate: GRADLE_SYNC_SUCCEEDED trace sent successfully")
    resetState()
  }

  override fun syncFailed(project: Project, errorMessage: String) {
    super.syncFailed(project, errorMessage)
    logger.info("Skate: syncFailed() called for project: ${project.name}")
    val tracingEnabled = project.isTracingEnabled()
    logger.info("Skate: Tracing enabled: $tracingEnabled")
    if (!tracingEnabled) {
      logger.info("Skate: Skipping GRADLE_SYNC_FAILED trace - tracing disabled")
      return
    }
    val startTime = syncStartTime ?: Instant.now()
    val endTime = Instant.now()
    val durationMs = java.time.Duration.between(startTime, endTime).toMillis()

    logger.info("Skate: Sending GRADLE_SYNC_FAILED trace (duration: ${durationMs}ms, error: $errorMessage)")
    val spanBuilder = SkateSpanBuilder().apply {
      addTag("event", SkateTracingEvent.GradleSync.GRADLE_SYNC_FAILED)
      addTag("duration_ms", durationMs)
      addTag("success", false)
      addTag("error_message", errorMessage)
    }
    sendTrace(
      project,
      startTime,
      spanBuilder,
      spanId = makeId(),
      parentId = parentId,
    )
    logger.info("Skate: GRADLE_SYNC_FAILED trace sent successfully")
    resetState()
  }

  private fun resetState() {
    syncStartTime = null
    parentId = ByteString.EMPTY
  }

  private fun sendTrace(
    project: Project,
    startTimestamp: Instant,
    spanBuilder: SkateSpanBuilder,
    spanId: ByteString,
    parentId: ByteString,
  ) {
    project
      .getTraceReporter()
      .createPluginUsageTraceAndSendTrace(
        "gradle_sync",
        startTimestamp,
        spanBuilder.getKeyValueList(),
        spanId,
        parentId,
      )
  }
}
