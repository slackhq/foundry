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
package foundry.intellij.skate.tracing

sealed interface SkateTracingEvent {
  val name: String

  enum class ProjectGen : SkateTracingEvent {
    DIALOG_OPENED
  }

  enum class WhatsNew : SkateTracingEvent {
    PANEL_OPENED,
    PANEL_CLOSED,
  }

  enum class HoustonFeatureFlag : SkateTracingEvent {
    HOUSTON_FEATURE_FLAG_URL_CLICKED
  }

  enum class ModelTranslator : SkateTracingEvent {
    MODEL_TRANSLATOR_GENERATED
  }

  enum class Indexing : SkateTracingEvent {
    INDEXING_REASON,
    DUMB_MODE_WITHOUT_PAUSE_DURATION,
    PAUSED_DURATION,
    UPDATING_TIME,
    WAS_INTERRUPTED,
    SCANNING_TYPE,
    INDEXING_COMPLETED,
    DUMB_INDEXING_COMPLETED,
  }

  enum class GradleSync : SkateTracingEvent {
    GRADLE_SYNC_STARTED,
    GRADLE_SYNC_SUCCEDDED,
    GRADLE_SYNC_SKIPPED,
  }
}
