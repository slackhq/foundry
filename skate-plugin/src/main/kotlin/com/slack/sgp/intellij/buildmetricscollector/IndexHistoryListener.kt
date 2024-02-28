
package com.slack.sgp.intellij.buildmetricscollector

import com.intellij.util.indexing.diagnostic.ProjectIndexingHistory
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistoryListener
import com.intellij.util.indexing.diagnostic.dto.toMillis
import com.slack.sgp.intellij.tracing.IndexingEvent
import com.slack.sgp.intellij.tracing.SkateSpanBuilder
import com.slack.sgp.intellij.tracing.SkateTraceReporter
import com.slack.sgp.intellij.util.isTracingEnabled

@Suppress("UnstableApiUsage")
class IndexHistoryListener : ProjectIndexingHistoryListener {

  override fun onFinishedIndexing(projectIndexingHistory: ProjectIndexingHistory) {
    val project = projectIndexingHistory.project
    if (!project.isTracingEnabled()) return
    val skateSpanBuilder = SkateSpanBuilder()
    val tags: MutableMap<String, Any> = mutableMapOf()
    tags.apply {
      projectIndexingHistory.indexingReason?.let { put(IndexingEvent.INDEXING_REASON.name, it) }
      put(IndexingEvent.UPDATING_TIME.name, projectIndexingHistory.times.totalUpdatingTime.toMillis())
      put(IndexingEvent.SCAN_FILES_DURATION.name, projectIndexingHistory.times.scanFilesDuration.toMillis())
      put(IndexingEvent.INDEXING_DURATION.name, projectIndexingHistory.times.indexingDuration.toMillis())
      put(IndexingEvent.IS_INTERRUPTED.name, projectIndexingHistory.times.wasInterrupted)
      put(IndexingEvent.SCANNING_TYPE.name, projectIndexingHistory.times.scanningType.name)
    }
    skateSpanBuilder.addSpanTags(tags)
    SkateTraceReporter(project)
      .createPluginUsageTraceAndSendTrace(
        "indexing",
        projectIndexingHistory.times.updatingStart.toInstant(),
        skateSpanBuilder.getKeyValueList(),
      )
  }
}
