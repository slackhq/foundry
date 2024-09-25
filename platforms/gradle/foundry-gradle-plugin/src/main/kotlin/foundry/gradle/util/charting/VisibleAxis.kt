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

internal sealed class VisibleAxis : ChartProperty {
  override val key: String = "chxt"

  object XAxis : VisibleAxis() {
    override val value: String = "x"
  }

  object TAxis : VisibleAxis() {
    override val value: String = "t"
  }

  object YAxis : VisibleAxis() {
    override val value: String = "y"
  }

  object RAxis : VisibleAxis() {
    override val value: String = "r"
  }

  private data class MultipleVisibleAxes(private val axes: Set<VisibleAxis>) : VisibleAxis() {
    override val value: String = axes.joinToString(separator = ",") { it.value }

    override operator fun plus(axis: VisibleAxis): VisibleAxis {
      return MultipleVisibleAxes(axes + axis)
    }
  }

  open operator fun plus(axis: VisibleAxis): VisibleAxis {
    return MultipleVisibleAxes(setOf(this, axis))
  }
}
