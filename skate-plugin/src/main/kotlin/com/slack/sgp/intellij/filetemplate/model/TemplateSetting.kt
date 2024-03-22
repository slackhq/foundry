package com.slack.sgp.intellij.filetemplate.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TemplateSetting(val name: String, @SerialName("file_name_suffix") val fileNameSuffix: String)
