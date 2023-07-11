package com.slack.sgp.intellij

import com.intellij.util.xmlb.Converter
import java.time.LocalDateTime
import java.time.ZoneId

class LocalDateTimeConverter : Converter<LocalDateTime>() {
  override fun fromString(value: String): LocalDateTime {
    val epochMilli = java.lang.Long.parseLong(value)
    val zoneId = ZoneId.systemDefault()
    return java.time.Instant.ofEpochMilli(epochMilli).atZone(zoneId).toLocalDateTime()
  }

  override fun toString(value: LocalDateTime): String {
    val zoneId = ZoneId.systemDefault()
    val epochMilli = value.atZone(zoneId).toInstant().toEpochMilli()
    return java.lang.Long.toString(epochMilli)
  }
}
