/*
 * Copyright (C) 2026 Slack Technologies, LLC
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
package foundry.gradle.android

/** A compileSdk API level with an optional minor API level, such as `37` or `37.2`. */
internal data class AndroidCompileSdk(val major: Int, val minor: Int?) {
  internal companion object {
    /** Parses `37`, `37.2`, or the legacy `android-37` and `android-37.2` forms. */
    fun parse(value: String): AndroidCompileSdk {
      val version = value.removePrefix("android-")
      val major = version.substringBefore('.')
      val minor = version.substringAfter('.', missingDelimiterValue = "").ifEmpty { null }
      return AndroidCompileSdk(
        major = major.toIntOrNull() ?: invalid(value),
        minor = minor?.let { it.toIntOrNull() ?: invalid(value) },
      )
    }

    private fun invalid(value: String): Nothing =
      error(
        "Invalid foundry.android.compileSdkVersion '$value', expected a form like '37' or '37.2'"
      )
  }
}
