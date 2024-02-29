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

import com.intellij.util.indexing.diagnostic.ProjectIndexingHistory
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistoryListener
import com.intellij.util.indexing.diagnostic.dto.toMillis
import com.slack.sgp.intellij.tracing.IndexingEvent
import com.slack.sgp.intellij.tracing.SkateSpanBuilder
import com.slack.sgp.intellij.tracing.SkateTraceReporter
import com.slack.sgp.intellij.util.isTracingEnabled

@Suppress("UnstableApiUsage")
class IndexingListener : ProjectIndexingHistoryListener {
  override fun onFinishedIndexing(projectIndexingHistory: ProjectIndexingHistory) {
    val project = projectIndexingHistory.project
    if (!project.isTracingEnabled()) return
    val skateSpanBuilder = SkateSpanBuilder()
    val tags: MutableMap<String, Any> = mutableMapOf()
    tags.apply {
      projectIndexingHistory.indexingReason?.let {
        put(IndexingEvent.INDEXING_REASON.name.take(200), it)
      }
      put(
        IndexingEvent.UPDATING_TIME.name,
        projectIndexingHistory.times.totalUpdatingTime.toMillis(),
      )
      put(
        IndexingEvent.SCAN_FILES_DURATION.name,
        projectIndexingHistory.times.scanFilesDuration.toMillis(),
      )
      put(
        IndexingEvent.INDEXING_DURATION.name,
        projectIndexingHistory.times.indexingDuration.toMillis(),
      )
      put(IndexingEvent.IS_INTERRUPTED.name, projectIndexingHistory.times.wasInterrupted)
      put(IndexingEvent.SCANNING_TYPE.name, projectIndexingHistory.times.scanningType.name)
      put("event", IndexingEvent.INDEXING_COMPLETED.name)
    }
    skateSpanBuilder.addSpanTags(tags)
    SkateTraceReporter(project)
      .createPluginUsageTraceAndSendTrace(
        "indexing",
        projectIndexingHistory.times.updatingStart.toInstant(),
        skateSpanBuilder.getKeyValueList(),
      )
  }

  override fun onStartedIndexing(projectIndexingHistory: ProjectIndexingHistory) {}
}
