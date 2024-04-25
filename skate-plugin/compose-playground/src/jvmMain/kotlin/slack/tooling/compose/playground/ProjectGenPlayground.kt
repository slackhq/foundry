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
import slack.tooling.projectgen.ProjectGenUi
import slack.tooling.projectgen.ProjectGenUi.ProjectGenApp

fun main() = singleWindowApplication {
  var isDark by remember { mutableStateOf(false) }
  IntUiTheme(isDark) {
    Column(Modifier.background(JewelTheme.globalColors.paneBackground)) {
      DefaultButton(modifier = Modifier.padding(16.dp), onClick = { isDark = !isDark }) {
        Text("Toggle dark mode")
      }
      ProjectGenApp(
        rootDir = "rootDir",
        events =
          object : ProjectGenUi.Events {
            override fun doOKAction() {
              println("doOKAction")
            }

            override fun dismissDialogAndSync() {
              println("dismissDialogAndSync")
            }
          },
      )
    }
  }
}
