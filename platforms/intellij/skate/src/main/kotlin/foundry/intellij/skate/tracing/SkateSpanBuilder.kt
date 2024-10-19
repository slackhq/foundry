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
package foundry.intellij.skate.tracing

import foundry.tracing.KeyValue
import foundry.tracing.model.TagBuilder
import foundry.tracing.model.newTagBuilder
import java.util.*

class SkateSpanBuilder {
  private val keyValueList: TagBuilder = newTagBuilder()

  fun addTag(key: String, value: String) {
    keyValueList.apply { key.lowercase(Locale.US) tagTo value }
  }

  fun addTag(key: String, event: SkateTracingEvent) {
    keyValueList.apply { key.lowercase(Locale.US) tagTo event.name }
  }

  fun addTag(key: String, value: Long) {
    keyValueList.apply { key.lowercase(Locale.US) tagTo value }
  }

  fun addTag(key: String, value: Boolean) {
    keyValueList.apply { key.lowercase(Locale.US) tagTo value }
  }

  fun getKeyValueList(): List<KeyValue> {
    return keyValueList.toList()
  }
}
