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
package foundry.common.json

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.addAdapter
import com.squareup.moshi.rawType
import foundry.common.RegexMap
import foundry.common.buildRegexMap
import java.io.File
import java.lang.reflect.Type
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.buffer
import okio.sink
import okio.source

public object JsonTools {
  public val MOSHI: Moshi =
    Moshi.Builder()
      .addAdapter<Regex>(RegexJsonAdapter())
      .addAdapter<LocalDateTime>(LocalDateTimeJsonAdapter())
      .add(RegexMapJsonAdapter.Factory())
      .build()

  public inline fun <reified T : Any> fromJson(path: Path): T {
    return fromJson(path.source().buffer())
  }

  public inline fun <reified T : Any> fromJson(file: File): T {
    return fromJson(file.source().buffer())
  }

  public inline fun <reified T : Any> fromJson(source: BufferedSource): T {
    return source.use { MOSHI.adapter<T>().fromJson(it)!! }
  }

  public inline fun <reified T : Any> fromJsonValue(value: Any): T {
    return MOSHI.adapter<T>().fromJsonValue(value)!!
  }

  public fun Buffer.readJsonValueMap(): Map<String, Any> {
    @Suppress("UNCHECKED_CAST")
    return JsonReader.of(this).use { it.readJsonValue() } as Map<String, Any>
  }

  public inline fun <reified T : Any> toJson(value: T?, prettyPrint: Boolean = false): String {
    val buffer = Buffer()
    toJson(buffer, value, prettyPrint)
    return buffer.readUtf8()
  }

  public inline fun <reified T : Any> toJson(path: Path, value: T?, prettyPrint: Boolean = false) {
    return toJson(path.sink().buffer(), value, prettyPrint)
  }

  public inline fun <reified T : Any> toJson(file: File, value: T?, prettyPrint: Boolean = false) {
    return toJson(file.sink().buffer(), value, prettyPrint)
  }

  public inline fun <reified T : Any> toJsonBuffer(
    value: T?,
    prettyPrint: Boolean = false,
  ): Buffer {
    val buffer = Buffer()
    toJson(buffer, value, prettyPrint)
    return buffer
  }

  public inline fun <reified T : Any> toJson(
    sink: BufferedSink,
    value: T?,
    prettyPrint: Boolean = false,
  ) {
    return JsonWriter.of(sink)
      .apply {
        if (prettyPrint) {
          indent = "  "
        }
      }
      .use { MOSHI.adapter<T>().toJson(it, value) }
  }
}

// Used in FoundryTools for thermals data
private class LocalDateTimeJsonAdapter : JsonAdapter<LocalDateTime>() {

  override fun fromJson(reader: JsonReader): LocalDateTime? {
    if (reader.peek() == JsonReader.Token.NULL) {
      reader.skipValue()
      return null
    }
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

// Used in ModuleTopography
private class RegexJsonAdapter : JsonAdapter<Regex>() {
  override fun fromJson(reader: JsonReader): Regex? {
    if (reader.peek() == JsonReader.Token.NULL) {
      reader.skipValue()
      return null
    }
    return Regex(reader.nextString())
  }

  override fun toJson(writer: JsonWriter, value: Regex?) {
    if (value == null) {
      writer.nullValue()
      return
    }
    writer.value(value.pattern)
  }
}

private class RegexMapJsonAdapter(private val stringAdapter: JsonAdapter<String>) :
  JsonAdapter<RegexMap>() {

  class Factory : JsonAdapter.Factory {
    override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
      if (type.rawType != RegexMap::class.java) return null
      val stringAdapter = moshi.adapter<String>()
      return RegexMapJsonAdapter(stringAdapter).nullSafe()
    }
  }

  override fun fromJson(reader: JsonReader): RegexMap? {
    reader.beginObject()
    val map = buildRegexMap {
      while (reader.hasNext()) {
        reader.promoteNameToValue()
        val key = stringAdapter.fromJson(reader) ?: error("Null key at ${reader.path}")
        val value = stringAdapter.fromJson(reader) ?: error("Null value at ${reader.path}")
        // We don't check for duplicate keys in this implementation
        replace(key, value)
      }
    }
    reader.endObject()
    return map
  }

  override fun toJson(writer: JsonWriter, value: RegexMap?) {
    writer.beginObject()
    for ((key, value) in value!!) {
      writer.name(key.pattern)
      writer.value(value)
    }
    writer.endObject()
  }
}
