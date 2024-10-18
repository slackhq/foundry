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
package foundry.intellij.compose.projectgen

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test

class TextElementTest {
  @Test
  fun `test TextFieldState`() {
    val initialValue = "TextFieldState initial value"
    val textElement = TextElement(initialValue = initialValue, label = "Label")
    assertEquals(initialValue, textElement.state.text)
  }

  @Test
  fun `test TextFieldState value change`() {
    val textElement = TextElement(initialValue = "TextFieldState initial value", label = "Label")

    val newValue = "TextFieldState new value"
    textElement.value = newValue
    assertEquals(newValue, textElement.state.text)
  }

  @Test
  fun `test TextFieldState reset value`() {
    val textElement = TextElement(initialValue = "TextFieldState initial value", label = "Label")

    textElement.value = "Changed Text"
    assertEquals("Changed Text", textElement.value)

    textElement.reset()
    assertEquals("TextFieldState initial value", textElement.state.text)
  }

  @Test
  fun `test TextFieldState visibility`() {
    val textElement = TextElement(initialValue = "TextFieldState initial value", label = "Label")
    assertTrue(textElement.isVisible)

    textElement.isVisible = false
    assertEquals(textElement.isVisible, false)

    textElement.reset()
    assertEquals("TextFieldState initial value", textElement.state.text)
  }
}
