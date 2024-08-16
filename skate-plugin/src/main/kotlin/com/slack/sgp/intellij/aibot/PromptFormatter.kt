package com.slack.sgp.intellij.aibot

interface PromptFormatter {
  fun getUIPrompt(): String

  fun getRequestPrompt(): String
}
