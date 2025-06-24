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

import com.intellij.util.indexing.diagnostic.ProjectDumbIndexingHistory
import com.intellij.util.indexing.diagnostic.ProjectIndexingActivityHistoryListener
import com.intellij.util.indexing.diagnostic.ProjectScanningHistory
import com.intellij.util.indexing.diagnostic.dto.toMillis
import foundry.intellij.skate.tracing.SkateSpanBuilder
import foundry.intellij.skate.tracing.SkateTracingEvent
import foundry.intellij.skate.util.getTraceReporter
import foundry.intellij.skate.util.isTracingEnabled

@Suppress("UnstableApiUsage")
class IndexingListener : ProjectIndexingActivityHistoryListener {

  override fun onFinishedScanning(history: ProjectScanningHistory) {
    super.onFinishedScanning(history)
    val skateSpanBuilder = SkateSpanBuilder()
    skateSpanBuilder.apply {
      history.scanningReason?.let { addTag(SkateTracingEvent.Indexing.INDEXING_REASON.name, it) }
      addTag(SkateTracingEvent.Indexing.SCANNING_TYPE.name, history.times.scanningType.name)
      addTag(
        SkateTracingEvent.Indexing.UPDATING_TIME.name,
        history.times.totalUpdatingTime.toMillis(),
      )
      addTag(SkateTracingEvent.Indexing.WAS_INTERRUPTED.name, history.times.isCancelled)
      addTag(
        SkateTracingEvent.Indexing.UPDATING_TIME.name,
        history.times.totalUpdatingTime.toMillis(),
      )
      addTag(
        SkateTracingEvent.Indexing.DUMB_MODE_WITHOUT_PAUSE_DURATION.name,
        history.times.dumbModeWithoutPausesDuration.toMillis(),
      )
      addTag(
        SkateTracingEvent.Indexing.PAUSED_DURATION.name,
        history.times.pausedDuration.toMillis(),
      )
      addTag("event", SkateTracingEvent.Indexing.INDEXING_COMPLETED.name)
    }
    history.project
      .getTraceReporter()
      .createPluginUsageTraceAndSendTrace(
        "indexing",
        history.times.updatingStart.toInstant(),
        skateSpanBuilder.getKeyValueList(),
      )
  }

  override fun onFinishedDumbIndexing(history: ProjectDumbIndexingHistory) {
    val currentProject = history.project
    if (!currentProject.isTracingEnabled()) return
    val skateSpanBuilder = SkateSpanBuilder()
    skateSpanBuilder.apply {
      addTag(
        SkateTracingEvent.Indexing.UPDATING_TIME.name,
        history.times.totalUpdatingTime.toMillis(),
      )
      addTag(SkateTracingEvent.Indexing.WAS_INTERRUPTED.name, history.times.isCancelled)
      addTag("event", SkateTracingEvent.Indexing.DUMB_INDEXING_COMPLETED.name)
    }
    currentProject
      .getTraceReporter()
      .createPluginUsageTraceAndSendTrace(
        "indexing",
        history.times.updatingStart.toInstant(),
        skateSpanBuilder.getKeyValueList(),
      )
  }
}
