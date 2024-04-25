package slack.tooling.compose.playground

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.singleWindowApplication
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import slack.tooling.markdown.ui.MarkdownContent

fun main() = singleWindowApplication {
  var isDark by remember { mutableStateOf(false) }
  IntUiTheme(isDark) {
    Column(Modifier.background(JewelTheme.globalColors.paneBackground)) {
      DefaultButton(modifier = Modifier.padding(16.dp), onClick = { isDark = !isDark }) {
        Text("Toggle dark mode")
      }
      MarkdownContent { MARKDOWN }
    }
  }
}

private const val MARKDOWN =
  """
  # Markdown Playground
  
  ---
  
  # This is an H1

  ## This is an H2

  ### This is an H3

  #### This is an H4

  ##### This is an H5

  ###### This is an H6
  
  This is a paragraph with some *italic* and **bold** text.
  
  This is a paragraph with some `inline code`.
  
  This is a paragraph with a [link](https://www.jetbrains.com/).
  
  This is a code block:
  ```kotlin
  fun main() {
    println("Hello, world!")
  }
  ```
  
  > This is a block quote.
  
  This is a divider
  
  ---
  
  The above was supposed to be a divider.
  
  ~~This is strikethrough~~
  
  This is an ordered list:
  1. Item 1
  2. Item 2
  3. Item 3
  
  This is an unordered list with dashes:
  - Item 1
  - Item 2
  - Item 3
  
  This is an unordered list with asterisks:
  * Item 1
  * Item 2
  * Item 3
"""
