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
package slack.tooling.projectgen

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Stable
internal sealed interface UiElement {
  fun reset() {}

  var isVisible: Boolean
}

@Immutable
internal object DividerElement : UiElement {
  override var isVisible: Boolean = true
}

@Immutable
internal data class SectionElement(
  val title: String,
  val description: String,
  val indentLevel: Int = 0,
) : UiElement {
  override var isVisible: Boolean = true
}

internal class CheckboxElement(
  private val initialValue: Boolean,
  val name: String,
  val hint: String,
  val indentLevel: Int = 0,
  isVisible: Boolean = true,
) : UiElement {
  var isChecked by mutableStateOf(initialValue)

  override fun reset() {
    isChecked = initialValue
  }

  override var isVisible: Boolean by mutableStateOf(isVisible)
}

@Suppress("LongParameterList")
internal class TextElement(
  private val initialValue: String,
  val label: String,
  val description: String? = null,
  val indentLevel: Int = 0,
  val readOnly: Boolean = false,
  val prefixTransformation: String? = null,
  private val initialVisibility: Boolean = true,
  // List of tags this element depends on
  private val dependentElements: List<CheckboxElement> = emptyList(),
  val validationRegex: Regex? = null,
) : UiElement {
  var value by mutableStateOf(initialValue)

  val enabled by derivedStateOf { !readOnly && dependentElements.all { it.isChecked } }

  val isValid by derivedStateOf {
    validationRegex?.let { value.isNotBlank() && value.matches(it) } != false
  }

  override fun reset() {
    value = initialValue
    isVisible = initialVisibility
  }

  override var isVisible: Boolean by mutableStateOf(initialVisibility)
}

internal class ExclusiveCheckboxElement(
  initialValue: String,
  val options: List<String>,
  val indentLevel: Int = 0,
  isVisible: Boolean = true,
) : UiElement {
  override var isVisible: Boolean by mutableStateOf(isVisible)
  var selectedCheckbox by mutableStateOf(initialValue)
}
