/*
 * Copyright (C) 2023 Slack Technologies, LLC
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

import com.slack.sgp.tracing.KeyValue
import com.slack.sgp.tracing.model.TagBuilder
import com.slack.sgp.tracing.model.newTagBuilder

class SkateSpanBuilder {
  private val keyValueList: TagBuilder = newTagBuilder()

  fun addSpanTag(key: String, value: String) {
    keyValueList.apply { key.lowercase() tagTo value }
  }

  fun addSpanTag(key: String, event: SkateTracingEvent) {
    keyValueList.apply { key.lowercase() tagTo event.name }
  }

  fun addSpanTags(tags: Map<String, Any>) {
    tags.forEach { (key, value) ->
      when (value) {
        is String -> keyValueList.apply { key.lowercase() tagTo value }
        is Double -> keyValueList.apply { key.lowercase() tagTo value }
        is Long -> keyValueList.apply { key.lowercase() tagTo value }
        is Boolean -> keyValueList.apply { key.lowercase() tagTo value }
        else -> throw IllegalArgumentException("Unsupported value type for key: ${key}")
      }
    }
  }

  fun getKeyValueList(): List<KeyValue> {
    return keyValueList.toList()
  }
}

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
  IS_INTERRUPTED,
  SCANNING_TYPE,
  INDEXING_COMPLETED,
}
