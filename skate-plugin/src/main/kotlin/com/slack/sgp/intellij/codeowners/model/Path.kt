package com.slack.sgp.intellij.codeowners.model

import kotlinx.serialization.Serializable

@Serializable
data class Path(val path: String, val notify: Boolean?)