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

import com.slack.sgp.tracing.KeyValue
import com.slack.sgp.tracing.ValueType
import okio.ByteString

// Functions for creating various types of KeyValues

public infix fun String.tagTo(value: String): KeyValue =
  KeyValue(key = this, v_type = ValueType.STRING, v_str = value)

public infix fun String.tagTo(value: Boolean): KeyValue =
  KeyValue(key = this, v_type = ValueType.BOOL, v_bool = value)

public infix fun String.tagTo(value: Long): KeyValue =
  KeyValue(key = this, v_type = ValueType.INT64, v_int64 = value)

public infix fun String.tagTo(value: Double): KeyValue =
  KeyValue(key = this, v_type = ValueType.FLOAT64, v_float64 = value)

public infix fun String.tagTo(value: ByteString): KeyValue =
  KeyValue(key = this, v_type = ValueType.BINARY, v_binary = value)
