@file:Suppress("UnstableApiUsage")

package com.slack.sgp.intellij.buildmetricscollector

import com.intellij.util.indexing.diagnostic.ProjectIndexingHistory
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistoryListener
import com.intellij.util.indexing.diagnostic.dto.toMillis
import com.slack.sgp.intellij.tracing.SkateSpanBuilder
import com.slack.sgp.intellij.tracing.SkateTraceReporter
import com.slack.sgp.intellij.tracing.SkateTracingEvent
import com.intellij.openapi.diagnostic.logger
class IndexHistoryListener : ProjectIndexingHistoryListener {

  override fun onStartedIndexing(projectIndexingHistory: ProjectIndexingHistory) {
    logger<IndexHistoryListener>().info("IN HERERER STARTING INDEXING")
    super.onStartedIndexing(projectIndexingHistory)
  }

  override fun onFinishedIndexing(projectIndexingHistory: ProjectIndexingHistory) {
    logger<IndexHistoryListener>().info("IN HERERER INDEXING DONEEE")
    val project = projectIndexingHistory.project

    val skateSpanBuilder = SkateSpanBuilder()
    val tags: MutableMap<String, Any> = mutableMapOf()
    tags.apply {
      projectIndexingHistory.indexingReason?.let { put("indexing_reason", it) }
      put("event", SkateTracingEvent.EventType.INDEXING)
      put("updating_time", projectIndexingHistory.times.totalUpdatingTime.toMillis())
      put("scan_files_duration", projectIndexingHistory.times.scanFilesDuration.toMillis())
      put("indexing_duration", projectIndexingHistory.times.indexingDuration.toMillis())
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