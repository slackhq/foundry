package com.slack.sgp.intellij.filetemplate.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FileTemplateSettings(
  @SerialName("templates") val templates: List<TemplateSetting>,
)
