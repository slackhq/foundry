/*
 * Copyright (C) 2022 Slack Technologies, LLC
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
package slack.gradle.util

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.addAdapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

internal object JsonTools {
  val MOSHI =
    Moshi.Builder()
      // Used in SlackTools for thermals data
      .addAdapter<LocalDateTime>(LocalDateTimeJsonAdapter())
      .addLast(KotlinJsonAdapterFactory())
      .build()
}

private class LocalDateTimeJsonAdapter : JsonAdapter<LocalDateTime>() {

  override fun fromJson(reader: JsonReader): LocalDateTime {
    val timestamp = reader.nextLong()
    return LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault())
  }

  override fun toJson(writer: JsonWriter, value: LocalDateTime?) {
    if (value == null) {
      writer.nullValue()
      return
    }

    val timestamp = value.atZone(ZoneId.systemDefault()).toEpochSecond()
    writer.value(timestamp)
  }
}
