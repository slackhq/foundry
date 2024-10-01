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
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.rawType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/** Decodes a List<T> that could also come down the wire as a single element. */
internal class SingleItemListJsonAdapterFactory : JsonAdapter.Factory {
  @Suppress("ReturnCount")
  override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
    if (annotations.isNotEmpty()) return null

    val rawType = type.rawType
    if (type is ParameterizedType && List::class.java.isAssignableFrom(rawType)) {
      // It's a List!
      val elementType = Types.collectionElementType(type, rawType)
      val delegateListAdapter = moshi.nextAdapter<List<*>>(this, type, annotations)
      // Get a reusable FuzzyString adapter to get strings from all JSON primitives
      val elementTypeAdapter = moshi.adapter<Any>(elementType)
      return ListJsonAdapter(delegateListAdapter, elementTypeAdapter)
    }

    return null
  }

  private class ListJsonAdapter<T>(
    private val listAdapter: JsonAdapter<List<T>>,
    private val collectionTypeAdapter: JsonAdapter<T>,
  ) : JsonAdapter<List<T>>() {
    override fun toJson(writer: JsonWriter, value: List<T>?) {
      listAdapter.toJson(writer, value)
    }

    override fun fromJson(reader: JsonReader): List<T>? {
      val result: List<T>?
      when (reader.peek()) {
        JsonReader.Token.NULL -> {
          // Carry over null value
          result = reader.nextNull()
        }
        JsonReader.Token.BEGIN_ARRAY -> {
          // Happy path, expected list type
          reader.beginArray()
          result = buildList {
            while (reader.hasNext()) {
              add(collectionTypeAdapter.fromJson(reader)!!)
            }
          }
          reader.endArray()
        }
        JsonReader.Token.BEGIN_OBJECT -> {
          throw JsonDataException(
            "Expected BEGIN_ARRAY but was BEGIN_OBJECT at path ${reader.path}"
          )
        }
        else -> {
          // Single element, try to decode as a single item
          result = listOf(collectionTypeAdapter.fromJson(reader)!!)
        }
      }
      return result
    }

    override fun toString() = "FuzzyJsonAdapter(List<T>)"
  }
}
