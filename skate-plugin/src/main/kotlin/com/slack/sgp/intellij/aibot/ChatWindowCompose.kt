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
import androidx.compose.ui.unit.dp

@Composable
fun ChatWindowCompose(){
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Bottom
    ){
        ConversationField()
    }
}

@Composable
fun ConversationField(){
    var text by remember { mutableStateOf("") }
    Row(){
        TextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Start your conversation") },
            modifier = Modifier
                .weight(1f)
        )
        Column(){
            Box(
                modifier = Modifier
                    .clickable{
                        text = ""
                    }
                    .defaultMinSize(minWidth = 56.dp)
                    .padding(8.dp)
            ){
                Text(
                    "Send",
                    color = MaterialTheme.colorScheme.surface
                )
            }
            Box(
                modifier = Modifier
                    .clickable{
                        text = ""
                    }
                    .defaultMinSize(minWidth = 56.dp)
                    .padding(8.dp)
            ){
                Text(
                    "Clear Chat",
                    color = MaterialTheme.colorScheme.surface
                )
            }
        }
    }
}