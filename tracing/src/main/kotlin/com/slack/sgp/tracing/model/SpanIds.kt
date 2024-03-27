/*
 * Copyright (C) 2023 Slack Technologies, LLC
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
package com.slack.sgp.tracing.model

import java.util.Locale
import kotlin.random.Random
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

/** Creates a new ID for a span or trace. */
public fun makeId(seed: Int? = null): ByteString = random16HexString(seed).encodeUtf8()

private const val BYTES_FOR_16_HEX_DIGITS = 8

/** Returns a [String] of 16 random hexadecimal digits. */
private fun random16HexString(seed: Int?): String {
  val bytes = ByteArray(BYTES_FOR_16_HEX_DIGITS)
  val random = seed?.let { Random(seed) } ?: Random.Default
  random.nextBytes(bytes)
  return bytes.joinToString("") { "%02x".format(Locale.US, it) }
}
