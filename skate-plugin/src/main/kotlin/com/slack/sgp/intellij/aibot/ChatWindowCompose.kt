package com.slack.sgp.intellij.aibot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ChatWindowCompose(){
    Column(modifier = Modifier.padding(all = 8.dp)){
        Text(
            text = "Welcome to the DevXP AI Chat Bot!",
            color = Color.White
        )
    }
}