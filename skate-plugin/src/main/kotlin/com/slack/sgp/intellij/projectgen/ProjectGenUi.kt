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
package com.slack.sgp.intellij.projectgen

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import slack.tooling.projectgen.CheckboxElement
import slack.tooling.projectgen.DividerElement
import slack.tooling.projectgen.SectionElement
import slack.tooling.projectgen.TextElement

private const val INDENT_SIZE = 16 // dp

// @OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProjectGen(state: ProjectGenScreen.State, modifier: Modifier = Modifier) {
  if (state.showDoneDialog) {
    StatusDialog(
      text = "Project generated successfully!",
      confirmButtonText = "Quit and Sync",
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
  Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
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
              Divider()
            }
            is SectionElement -> {
              Column {
                Text(element.title, style = MaterialTheme.typography.titleLarge)
                Text(element.description, style = MaterialTheme.typography.bodySmall)
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
                TextField(
                  element.value,
                  label = { Text(element.label) },
                  onValueChange = { newValue -> element.value = newValue },
                  visualTransformation =
                    element.prefixTransformation?.let(::PrefixTransformation)
                      ?: VisualTransformation.None,
                  readOnly = element.readOnly,
                  enabled = element.enabled,
                  singleLine = true,
                  isError =
                    element.value.isNotEmpty() &&
                      !element.value.matches(Regex("[a-zA-Z]([A-Za-z0-9\\-_:.])*")),
                )
                element.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
              }
            }
          }
        }
        Button(
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
    Column {
      Text(name)
      Text(text = hint, style = MaterialTheme.typography.bodySmall)
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
  @Suppress("ComposeM2Api")
  (AlertDialog(
    onDismissRequest = { onQuit() },
    confirmButton = { Button(onClick = { onConfirm() }) { Text(confirmButtonText) } },
    dismissButton = { Button(onClick = { onQuit() }) { Text("Quit") } },
    text = { Text(text) },
  ))
}
