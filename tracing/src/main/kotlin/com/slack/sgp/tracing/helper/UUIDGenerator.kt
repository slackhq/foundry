package com.slack.sgp.tracing.helper

import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import java.util.UUID

public class UUIDGenerator {
  public fun generateUUIDWith32Characters(): ByteString {
    return UUID.randomUUID().toString().replace("-", "").encodeUtf8()
  }

}