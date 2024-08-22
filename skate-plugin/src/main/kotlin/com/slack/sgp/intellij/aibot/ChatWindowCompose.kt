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
package com.slack.sgp.intellij.aibot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@Composable
fun ChatWindowCompose(modifier: Modifier = Modifier) {
  Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
    ConversationField()
  }
}

@Composable
fun ConversationField(modifier: Modifier = Modifier) {
  var textValue by remember { mutableStateOf(TextFieldValue("")) }
  Row(modifier) {
    TextField(
      value = textValue,
      onValueChange = { newText -> textValue = newText },
      label = { Text("Start your conversation") },
      modifier = Modifier.weight(1f),
    )
    Column() {
      Box(modifier = Modifier.clickable {}.defaultMinSize(minWidth = 56.dp).padding(8.dp)) {
        Text("Send", color = MaterialTheme.colorScheme.surface)
      }
      Box(
        modifier =
          Modifier.clickable { textValue = TextFieldValue("") }
            .defaultMinSize(minWidth = 56.dp)
            .padding(8.dp)
      ) {
        Text("Clear Chat", color = MaterialTheme.colorScheme.surface)
      }
    }
  }
}
