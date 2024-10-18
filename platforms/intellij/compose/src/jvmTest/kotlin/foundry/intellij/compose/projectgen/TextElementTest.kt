package foundry.intellij.compose.projectgen

import junit.framework.TestCase.assertEquals
import org.junit.Test

class TextElementTest {
    @Test
    fun `test TextFieldState` (){
        val initialValue = "TextFieldState initial value"
        val textElement = TextElement(
            initialValue = initialValue,
            label = "Label"
        )

        assertEquals(initialValue, textElement.state.text)
    }

    @Test
    fun `test TextFieldState value change` (){
        val textElement = TextElement(
            initialValue = "TextFieldState initial value",
            label = "Label"
        )

        val newValue = "TextFieldState new value"
        textElement.value = newValue
        assertEquals(newValue, textElement.state.text)
    }

    @Test
    fun `test TextFieldState reset value`() {
        val textElement = TextElement(
            initialValue = "TextFieldState initial value",
            label = "Label"
        )

        textElement.value = "Changed Text"
        assertEquals("Changed Text", textElement.value)

        textElement.reset()
        assertEquals("TextFieldState initial value", textElement.state.text)
    }
}