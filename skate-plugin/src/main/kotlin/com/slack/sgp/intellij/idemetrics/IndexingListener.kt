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
import com.slack.sgp.intellij.tracing.SkateSpanBuilder
import com.slack.sgp.intellij.tracing.SkateTracingEvent
import com.slack.sgp.intellij.util.getTraceReporter
import com.slack.sgp.intellij.util.isTracingEnabled

@Suppress("UnstableApiUsage")
class IndexingListener : ProjectIndexingHistoryListener {
  override fun onFinishedIndexing(projectIndexingHistory: ProjectIndexingHistory) {
    val currentProject = projectIndexingHistory.project
    if (!currentProject.isTracingEnabled()) return
    val skateSpanBuilder = SkateSpanBuilder()
    skateSpanBuilder.apply {
      projectIndexingHistory.indexingReason?.let {
        addTag(SkateTracingEvent.Indexing.INDEXING_REASON.name, it.take(200))
      }
      addTag(
        SkateTracingEvent.Indexing.UPDATING_TIME.name,
        projectIndexingHistory.times.totalUpdatingTime.toMillis(),
      )
      addTag(
        SkateTracingEvent.Indexing.SCAN_FILES_DURATION.name,
        projectIndexingHistory.times.scanFilesDuration.toMillis(),
      )
      addTag(
        SkateTracingEvent.Indexing.INDEXING_DURATION.name,
        projectIndexingHistory.times.indexingDuration.toMillis(),
      )
      addTag(
        SkateTracingEvent.Indexing.WAS_INTERRUPTED.name,
        projectIndexingHistory.times.wasInterrupted,
      )
      addTag(
        SkateTracingEvent.Indexing.SCANNING_TYPE.name,
        projectIndexingHistory.times.scanningType.name,
      )
      addTag("event", SkateTracingEvent.Indexing.INDEXING_COMPLETED.name)
    }
    currentProject
      .getTraceReporter()
      .createPluginUsageTraceAndSendTrace(
        "indexing",
        projectIndexingHistory.times.updatingStart.toInstant(),
        skateSpanBuilder.getKeyValueList(),
      )
  }

  override fun onStartedIndexing(projectIndexingHistory: ProjectIndexingHistory) {}
}
