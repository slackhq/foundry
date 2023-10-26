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
    keyValueList.apply { key tagTo value }
  }

  fun addSpanTag(key: String, value: SkateTracingEvent) {
    keyValueList.apply { key tagTo value.type.name }
  }

  fun getKeyValueList(): List<KeyValue> {
    return keyValueList.toList()
  }
}

class SkateTracingEvent(val type: EventType) {
  enum class EventType {
    PROJECT_GEN_OPENED,
    HOUSTON_FEATURE_FLAG_URL_CLICKED,
    SKATE_WHATS_NEW_PANEL_OPENED,
    SKATE_WHATS_NEW_PANEL_CLOSED
  }
}
