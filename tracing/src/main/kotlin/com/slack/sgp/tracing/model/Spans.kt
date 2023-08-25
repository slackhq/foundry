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
package com.slack.sgp.tracing.model

import com.slack.sgp.tracing.KeyValue
import com.slack.sgp.tracing.Span
import okio.ByteString

/** Creates a span. If no trace ID is specified, a random one is generated. */
@Suppress("LongParameterList")
public fun buildSpan(
  name: String,
  startTimestampMicros: Long,
  durationMicros: Long,
  traceId: ByteString = makeId(),
  parentId: ByteString = ByteString.EMPTY,
  addTags: MutableList<KeyValue>.() -> Unit = {},
): Span {
  return Span(
    id = makeId(),
    name = name,
    parent_id = parentId,
    trace_id = traceId,
    start_timestamp_micros = startTimestampMicros,
    duration_micros = durationMicros,
    tags = mutableListOf<KeyValue>().apply(addTags)
  )
}
