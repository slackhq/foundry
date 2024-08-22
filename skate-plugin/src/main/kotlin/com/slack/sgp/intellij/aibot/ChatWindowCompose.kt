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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

@Composable
fun ChatWindowCompose(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.fillMaxSize().background(JewelTheme.globalColors.paneBackground),
    verticalArrangement = Arrangement.Bottom,
  ) {
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
      modifier = Modifier.weight(1f).padding(4.dp).height(56.dp),
      placeholder = { Text("Start your conversation") },
    )
    Column() {
      DefaultButton(
        modifier = Modifier.defaultMinSize(minWidth = 56.dp).padding(4.dp),
        onClick = { textValue = TextFieldValue("") },
      ) {
        Text("Send")
      }
      Box(modifier = Modifier.clickable { textValue = TextFieldValue("") }.padding(4.dp)) {
        Text("Clear Chat")
      }
    }
  }
}
