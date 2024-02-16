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
internal data class SectionElement(val title: String, val description: String) : UiElement {
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
) : UiElement {
  var value by mutableStateOf(initialValue)

  val enabled by derivedStateOf { !readOnly && dependentElements.all { it.isChecked } }

  override fun reset() {
    value = initialValue
    isVisible = initialVisibility
  }

  override var isVisible: Boolean by mutableStateOf(initialVisibility)
}
