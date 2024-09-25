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

import slack.gradle.util.charting.ValuesEncoder.encodeExtendedSeries
import slack.gradle.util.charting.ValuesEncoder.encodePositionSeries

internal sealed class ChartData : ChartProperty {
  override val key: String = "chd"

  data class SimpleData(val data: List<Int>, val maxValue: Int = data.maxOrNull() ?: 0) :
    ChartData() {
    override val value: String
      get() = encodeExtendedSeries(data, maxValue)
  }

  data class TimeSeriesData(val data: Map<Int, Int>) : ChartData() {
    override val value: String
      get() = encodePositionSeries(data)
  }
}
