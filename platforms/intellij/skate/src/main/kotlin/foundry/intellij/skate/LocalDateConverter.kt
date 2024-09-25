/*
 * Copyright (C) 2024 Slack Technologies, LLC
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
package foundry.intellij.skate

import com.intellij.util.xmlb.Converter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

val MY_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

class LocalDateConverter : Converter<LocalDate>() {
  override fun fromString(value: String): LocalDate? {
    val trimmedValue = value.trim('_')

    return try {
      LocalDate.parse(trimmedValue, MY_DATE_FORMATTER)
    } catch (e: DateTimeParseException) {
      println("Error parsing date string: $trimmedValue")
      println(e.message)
      System.err.println(e.stackTraceToString())
      null
    }
  }

  override fun toString(value: LocalDate): String {
    return value.format(MY_DATE_FORMATTER)
  }
}
