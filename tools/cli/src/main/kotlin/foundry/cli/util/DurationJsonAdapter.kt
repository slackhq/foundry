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
package foundry.cli.util

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** A simple [Duration] adapter that converts a [Long] (in millis) to a [Duration]. */
internal class DurationJsonAdapter : JsonAdapter<Duration>() {
  override fun fromJson(reader: JsonReader): Duration {
    return reader.nextLong().milliseconds
  }

  override fun toJson(writer: JsonWriter, value: Duration?) {
    writer.value(value?.inWholeMilliseconds)
  }
}
