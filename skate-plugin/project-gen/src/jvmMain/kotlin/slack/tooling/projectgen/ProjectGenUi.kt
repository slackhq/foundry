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

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring.StiffnessMediumLow
import androidx.compose.animation.core.spring
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitContent
import com.slack.circuit.runtime.ui.ui
import javax.swing.JComponent
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Typography

object ProjectGenUi {
  @Stable
  interface Events {
    fun doOKAction()

    fun dismissDialogAndSync()
  }

  fun createPanel(rootDir: String, width: Int, height: Int, events: Events): JComponent {
    return ComposePanel().apply {
      setBounds(0, 0, width, height)
      setContent { ProjectGenApp(rootDir, events) }
    }
  }

  @Composable
  private fun ProjectGenApp(rootDir: String, events: Events) {
    val circuit = remember {
      Circuit.Builder()
        .addPresenterFactory { _, _, _ ->
          ProjectGenPresenter(
            rootDir = rootDir,
            onDismissDialog = events::doOKAction,
            onSync = events::dismissDialogAndSync,
          )
        }
        .addUiFactory { _, _ ->
          ui<ProjectGenScreen.State> { state, modifier -> ProjectGen(state, modifier) }
        }
        .build()
    }
    SlackDesktopTheme { CircuitContent(ProjectGenScreen, circuit = circuit) }
  }
}

private const val INDENT_SIZE = 16 // dp

@Composable
internal fun ProjectGen(state: ProjectGenScreen.State, modifier: Modifier = Modifier) {
  if (state.showDoneDialog) {
    StatusDialog(
      text = "Project generated successfully!",
      confirmButtonText = "Close and Sync",
      onQuit = { state.eventSink(ProjectGenScreen.Event.Quit) },
      onConfirm = { state.eventSink(ProjectGenScreen.Event.Sync) },
    )
  }
  if (state.showErrorDialog) {
    StatusDialog(
      text = "Failed to generate projects since project already exists",
      confirmButtonText = "Reset",
      onQuit = { state.eventSink(ProjectGenScreen.Event.Quit) },
      onConfirm = { state.eventSink(ProjectGenScreen.Event.Reset) },
    )
  }
  val scrollState = rememberScrollState(0)
  Box(modifier.fillMaxSize().background(JewelTheme.globalColors.paneBackground)) {
    CompositionLocalProvider(LocalContentColor provides JewelTheme.globalColors.infoContent) {
      Column(
        Modifier.padding(16.dp)
          .verticalScroll(scrollState)
          .animateContentSize(spring(stiffness = StiffnessMediumLow)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        for (element in state.uiElements) {
          if (!element.isVisible) continue
          when (element) {
            DividerElement -> {
              Divider(Orientation.Horizontal)
            }
            is SectionElement -> {
              Column {
                Text(element.title, style = Typography.h0TextStyle())
                Text(element.description)
              }
            }
            is CheckboxElement -> {
              Feature(
                element.name,
                element.hint,
                element.isChecked,
                indent = (element.indentLevel * INDENT_SIZE).dp,
                onEnabledChange = { element.isChecked = it },
              )
            }
            is TextElement -> {
              Column(Modifier.padding(start = (element.indentLevel * INDENT_SIZE).dp)) {
                //                val isError =
                //                  element.value.isNotEmpty() &&
                //                    !element.value.matches(Regex("[a-zA-Z]([A-Za-z0-9\\-_:.])*"))
                Text(text = element.label, style = Typography.h4TextStyle())
                TextField(
                  value = element.value,
                  onValueChange = { newValue -> element.value = newValue },
                  readOnly = element.readOnly,
                  enabled = element.enabled,
                  visualTransformation =
                    element.prefixTransformation?.let(::PrefixTransformation)
                      ?: VisualTransformation.None,
                  //                  singleLine = true,
                  //                  outline = if (isError) Outline.Error else Outline.None,
                )
                element.description?.let { Text(it) }
              }
            }
          }
        }
        DefaultButton(
          modifier = Modifier.fillMaxWidth(),
          enabled = state.canGenerate,
          onClick = { state.eventSink(ProjectGenScreen.Event.Generate) },
          content = { Text("Generate") },
        )
      }
      VerticalScrollbar(
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        adapter = rememberScrollbarAdapter(scrollState),
      )
    }
  }
}

@Composable
private fun Feature(
  name: String,
  hint: String,
  enabled: Boolean,
  modifier: Modifier = Modifier,
  indent: Dp = 0.dp,
  onEnabledChange: (Boolean) -> Unit,
) {
  Row(modifier.padding(start = indent), verticalAlignment = Alignment.CenterVertically) {
    Checkbox(checked = enabled, onCheckedChange = onEnabledChange)
    Spacer(Modifier.width(8.dp))
    Column {
      Text(name, style = Typography.h4TextStyle())
      Text(text = hint)
    }
  }
}

@Preview
@Composable
private fun PreviewFeature() {
  Feature(
    name = "Compose for Desktop",
    hint = "Enable Compose for Desktop support",
    enabled = true,
    onEnabledChange = {},
  )
}

@Composable
private fun StatusDialog(
  text: String,
  confirmButtonText: String,
  onQuit: () -> Unit,
  onConfirm: () -> Unit,
) {
  // No M3 AlertDialog in compose-jb yet
  // https://github.com/JetBrains/compose-multiplatform/issues/2037
  Popup(onDismissRequest = { onQuit() }) {
    Column {
      Text(text)
      Row {
        DefaultButton(onClick = { onConfirm() }) { Text(confirmButtonText) }
        DefaultButton(onClick = { onQuit() }) { Text("Close") }
      }
    }
  }
}
