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
package com.slack.sgp.intellij.tracing

interface SkateTracingEvent {
  val name: String
}

enum class ProjectGenEvent : SkateTracingEvent {
  PROJECT_GEN_OPENED
}

enum class WhatsNewEvent : SkateTracingEvent {
  WHATS_NEW_PANEL_OPENED,
  WHATS_NEW_PANEL_CLOSED
}

enum class HoustonFeatureFlagEvent : SkateTracingEvent {
  HOUSTON_FEATURE_FLAG_URL_CLICKED
}

enum class ModelTranslatorEvent : SkateTracingEvent {
  MODEL_TRANSLATOR_GENERATED
}

enum class IndexingEvent : SkateTracingEvent {
  INDEXING_REASON,
  UPDATING_TIME,
  SCAN_FILES_DURATION,
  INDEXING_DURATION,
  WAS_INTERRUPTED,
  SCANNING_TYPE,
  INDEXING_COMPLETED,
}
