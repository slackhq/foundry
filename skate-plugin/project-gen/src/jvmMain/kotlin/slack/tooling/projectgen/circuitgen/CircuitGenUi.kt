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
package slack.tooling.projectgen.circuitgen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.unit.dp
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitContent
import com.slack.circuit.runtime.ui.ui
import java.nio.file.Path
import javax.swing.JComponent
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Typography
import slack.tooling.projectgen.CheckboxElement
import slack.tooling.projectgen.DividerElement
import slack.tooling.projectgen.SectionElement
import slack.tooling.projectgen.SlackDesktopTheme
import slack.tooling.projectgen.TextElement

class CircuitGenUi {
  @Stable
  interface Events {
    fun doCancelAction()
  }

  fun createPanel(
    selectedDir: Path,
    baseTestClass: Map<String, String?>,
    events: Events,
    onFileGenerated: FileGenerationListener,
   ): JComponent {
    return ComposePanel().apply {
      setContent {
        CircuitGenApp(
          selectedDir,
          baseTestClass,
          events,
          onFileGenerated,
        ) { content ->
          SlackDesktopTheme(content = content)
        }
      }
    }
  }

  @Composable
  internal fun CircuitGenApp(
    selectedDir: Path,
    baseTestClass: Map<String, String?>,
    events: Events,
    onFileGenerated: FileGenerationListener,
    render: @Composable (content: @Composable () -> Unit) -> Unit,
  ) {
    val circuit = remember {
      Circuit.Builder()
        .addPresenterFactory { _, _, _ ->
          CircuitGenPresenter(
            selectedDir,
            baseTestClass,
            onFileGenerated,
            events::doCancelAction,
          )
        }
        .addUiFactory { _, _ ->
          ui<CircuitGenScreen.State> { state, modifier -> CircuitGen(state, modifier) }
        }
        .build()
    }
    render { CircuitContent(CircuitGenScreen, circuit = circuit) }
  }
}

@Composable
internal fun CircuitGen(state: CircuitGenScreen.State, modifier: Modifier = Modifier) {

  Box(modifier.fillMaxSize().background(JewelTheme.globalColors.paneBackground)) {
    val listState = rememberLazyListState()
    Column(Modifier.padding(16.dp)) {
      LazyColumn(
        modifier = Modifier.weight(1f),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        items(state.uiElements.filter { it.isVisible }) { element ->
          when (element) {
            is TextElement -> {
              Column(Modifier.padding(start = (element.indentLevel).dp)) {
                Text(text = element.label, style = Typography.h4TextStyle())
                Spacer(Modifier.height(4.dp))
                TextField(
                  value = element.value,
                  onValueChange = { newValue -> element.value = newValue },
                  modifier = Modifier.fillMaxWidth(),
                  enabled = element.enabled,
                )
              }
            }
            is CheckboxElement -> {
              Row(
                Modifier.padding(start = (element.indentLevel).dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Checkbox(checked = element.isChecked, onCheckedChange = { element.isChecked = it })
                Spacer(Modifier.width(8.dp))
                Column { Text(element.name, style = Typography.h4TextStyle()) }
              }
            }
            is SectionElement -> {
              Row(Modifier.padding(start = (element.indentLevel).dp)) {
                Text(element.title, style = Typography.h4TextStyle())
                Spacer(Modifier.width(8.dp))
                Text(element.description)
              }
            }
            DividerElement -> {
              Divider(Orientation.Horizontal)
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
          onClick = { state.eventSink(CircuitGenScreen.Event.Generate) },
          content = { Text("Generate") },
        )
      }
    }
  }
}
