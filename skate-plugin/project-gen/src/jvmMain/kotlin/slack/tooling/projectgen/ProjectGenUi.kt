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

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.Outline
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
      setContent {
        ProjectGenApp(rootDir, events) { content -> SlackDesktopTheme(content = content) }
      }
    }
  }

  @Composable
  internal fun ProjectGenApp(
    rootDir: String,
    events: Events,
    render: @Composable (content: @Composable () -> Unit) -> Unit,
  ) {
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
    render { CircuitContent(ProjectGenScreen, circuit = circuit) }
  }
}

private const val INDENT_SIZE = 16 // dp

@OptIn(ExperimentalFoundationApi::class)
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

  Box(modifier.fillMaxSize().background(JewelTheme.globalColors.paneBackground)) {
    val listState = rememberLazyListState()
    Column(Modifier.padding(16.dp)) {
      LazyColumn(
        modifier = Modifier.weight(1f),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        val visibleItems = state.uiElements.filter { it.isVisible }
        items(visibleItems) { element ->
          when (element) {
            DividerElement -> {
              Divider(Orientation.Horizontal)
            }
            is SectionElement -> {
              Column(Modifier.animateItemPlacement()) {
                Text(element.title, style = Typography.h1TextStyle())
                Text(element.description)
              }
            }
            is CheckboxElement -> {
              Feature(
                element.name,
                element.hint,
                element.isChecked,
                modifier = Modifier.animateItemPlacement(),
                indent = (element.indentLevel * INDENT_SIZE).dp,
                onEnabledChange = { element.isChecked = it },
              )
            }
            is TextElement -> {
              Column(
                Modifier.padding(start = (element.indentLevel * INDENT_SIZE).dp)
                  .animateItemPlacement()
              ) {
                Text(text = element.label, style = Typography.h4TextStyle())
                Spacer(Modifier.height(4.dp))
                TextField(
                  value = element.value,
                  onValueChange = { newValue -> element.value = newValue },
                  modifier = Modifier.fillMaxWidth(),
                  readOnly = element.readOnly,
                  enabled = element.enabled,
                  visualTransformation =
                    element.prefixTransformation?.let(::PrefixTransformation)
                      ?: VisualTransformation.None,
                  outline = if (element.isValid) Outline.None else Outline.Error,
                )
                element.description?.let {
                  Spacer(Modifier.height(4.dp))
                  Text(it)
                }
              }
            }
          }
        }
      }
      Divider(Orientation.Horizontal)
      Box(
        Modifier.background(JewelTheme.globalColors.paneBackground)
          .fillMaxWidth()
          .padding(top = 16.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.CenterEnd,
      ) {
        DefaultButton(
          enabled = state.canGenerate,
          onClick = { state.eventSink(ProjectGenScreen.Event.Generate) },
          content = { Text("Generate") },
        )
      }
    }
    VerticalScrollbar(
      modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
      adapter = rememberScrollbarAdapter(listState),
    )
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
fun StatusDialog(
  text: String,
  confirmButtonText: String,
  onQuit: () -> Unit,
  onConfirm: () -> Unit,
) {
  // No M3 AlertDialog in compose-jb yet
  // https://github.com/JetBrains/compose-multiplatform/issues/2037
  Popup(alignment = Alignment.Center, onDismissRequest = { onQuit() }) {
    Box(
      Modifier.width(600.dp)
        .height(100.dp)
        .background(JewelTheme.globalColors.paneBackground)
        .border(1.5.dp, JewelTheme.globalColors.borders.disabled, RoundedCornerShape(8.dp))
    ) {
      Column(
        Modifier.padding(20.dp).fillMaxHeight().fillMaxWidth(),
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(text)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          DefaultButton(onClick = { onConfirm() }) { Text(confirmButtonText) }
          Spacer(modifier = Modifier.width(8.dp))
          DefaultButton(onClick = { onQuit() }) { Text("Close") }
        }
      }
    }
  }
}
