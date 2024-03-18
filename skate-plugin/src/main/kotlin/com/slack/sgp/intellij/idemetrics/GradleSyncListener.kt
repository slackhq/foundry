package com.slack.sgp.intellij.idemetrics

import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.intellij.openapi.project.Project
import com.slack.sgp.intellij.tracing.SkateSpanBuilder
import com.slack.sgp.intellij.tracing.SkateTracingEvent
import com.slack.sgp.intellij.util.getTraceReporter
import com.slack.sgp.intellij.util.isTracingEnabled
import java.time.Instant
import java.util.UUID

class SkateGradleSyncListener : GradleSyncListener {

  private var parentId: String? = null
  override fun syncStarted(project: Project) {
    super.syncStarted(project)
    if (project.isTracingEnabled()) {
      val startTimestamp = Instant.now()
      parentId = UUID.randomUUID().toString()
      sendTrace(project, startTimestamp, SkateTracingEvent.GradleSync.GRADLE_SYNC_STARTED)
    }
  }

  override fun syncSucceeded(project: Project) {
    super.syncSucceeded(project)
    sendTrace(project, Instant.now(), SkateTracingEvent.GradleSync.GRADLE_SYNC_STARTED)
  }

  fun sendTrace(project: Project, startTimestamp: Instant, event: SkateTracingEvent) {
    val skateSpanBuilder = SkateSpanBuilder()
    skateSpanBuilder.addTag("event", SkateTracingEvent.ProjectGen.DIALOG_OPENED)
    project
      .getTraceReporter()
      .createPluginUsageTraceAndSendTrace(
        "project_generator",
        startTimestamp,
        skateSpanBuilder.getKeyValueList(),
        parentId
      )
}