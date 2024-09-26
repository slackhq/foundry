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
package foundry.gradle.stats

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// Example
// "HTML" :{
//  "nFiles": 1000,
//  "blank": 3575,
//  "comment": 0,
//  "code": 116111},
@JsonClass(generateAdapter = true)
internal data class LanguageStats(
  @Json(name = "nFiles") val files: Int,
  val code: Int,
  val comment: Int,
  val blank: Int,
) {

  val total: Int
    get() = code + comment + blank

  companion object {
    val EMPTY = LanguageStats(0, 0, 0, 0)
  }

  operator fun plus(other: LanguageStats): LanguageStats {
    return LanguageStats(
      files + other.files,
      code + other.code,
      comment + other.comment,
      blank + other.blank,
    )
  }
}

/** Merges this map with [other]. Keys present in both will have their values merged together. */
internal fun Map<String, LanguageStats>.mergeWith(
  other: Map<String, LanguageStats>
): Map<String, LanguageStats> {
  return (keys + other.keys)
    .associateWith { key -> listOfNotNull(get(key), other[key]) }
    .mapValues { (_, values) -> values.reduce(LanguageStats::plus) }
}
