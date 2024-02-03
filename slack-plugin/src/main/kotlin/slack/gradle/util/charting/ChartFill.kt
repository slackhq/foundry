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
package slack.gradle.util.charting

internal data class ChartFill(val type: FillType, val color: Color) : ChartProperty {
  override val key: String = "chf"

  override val value: String
    get() = "${type.type},s,${color.hex}"

  enum class FillType(val type: String) {
    TRANSPARENT("a"),
    BACKGROUND("bg"),
    CHART("c")
  }
}
