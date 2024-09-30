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
package slack.cli.util

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.rawType
import java.lang.reflect.Type

/** A simple [Regex] adapter that converts Strings to a [Regex]. */
internal class RegexJsonAdapter(private val stringAdapter: JsonAdapter<String>) :
  JsonAdapter<Regex>() {
  override fun fromJson(reader: JsonReader) = stringAdapter.fromJson(reader)!!.toRegex()

  override fun toJson(writer: JsonWriter, value: Regex?) {
    error("RegexJsonAdapter is only used for deserialization")
  }

  internal class Factory : JsonAdapter.Factory {
    override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
      return when (type.rawType) {
        Regex::class.java -> {
          RegexJsonAdapter(moshi.adapter<String>().nullSafe()).nullSafe()
        }
        else -> null
      }
    }
  }
}
