package com.slack.sgp.intellij.idemetrics

import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.intellij.openapi.project.Project
import com.slack.sgp.intellij.tracing.SkateSpanBuilder
import com.slack.sgp.intellij.tracing.SkateTracingEvent
import com.slack.sgp.intellij.util.getTraceReporter
import com.slack.sgp.intellij.util.isTracingEnabled
import com.slack.sgp.tracing.helper.UUIDGenerator
import okio.ByteString
import java.time.Instant
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
        parentId
      )
  }
}