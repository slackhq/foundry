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
package foundry.gradle.util.charting

import kotlin.math.floor

internal object ValuesEncoder {

  /**
   * Encodes a series of points into the Google Image Charts data spec. Values must be within the
   * range [0..4095].
   *
   * Taken from
   * https://developers.google.com/chart/image/docs/data_formats#javascript-encoding-script
   */
  fun encodeExtendedSeries(data: List<Int>, maxValue: Int = data.maxOrNull() ?: 0): String {
    var chartData = "e:"

    data.forEach { numericVal ->
      // Scale the value to maxVal.
      val scaledVal: Float =
        floor(EXTENDED_MAP_LENGTH * EXTENDED_MAP_LENGTH * (numericVal / maxValue.toFloat()))

      if (scaledVal > (EXTENDED_MAP_LENGTH * EXTENDED_MAP_LENGTH) - 1) {
        chartData += ".."
      } else if (scaledVal < 0) {
        chartData += "__"
      } else {
        // Calculate first and second digits and add them to the output.
        val quotient = floor(scaledVal / EXTENDED_MAP_LENGTH).toInt()
        val remainder = (scaledVal - EXTENDED_MAP_LENGTH * quotient).toInt()
        chartData += "${EXTENDED_MAP[quotient]}${EXTENDED_MAP[remainder]}"
      }
    }

    return chartData
  }

  fun encodePositionSeries(data: Map<Int, Int>): String {
    val xValues = mutableListOf<Int>()
    val yValues = mutableListOf<Int>()

    data.forEach { (key, value) ->
      xValues += key
      yValues += value
    }

    return "t:" +
      multiline(xValues.joinToString(separator = ","), yValues.joinToString(separator = ","))
  }

  private const val EXTENDED_MAP =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-."
  private const val EXTENDED_MAP_LENGTH = EXTENDED_MAP.length
}
