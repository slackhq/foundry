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
package com.slack.sgp.tracing.reporter

import com.slack.sgp.tracing.ListOfSpans
import com.slack.sgp.tracing.api.TracingService
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.wire.WireConverterFactory
import retrofit2.create

/** Uploads traces to the given [endpoint]. */
public class SimpleTraceReporter(
  private val endpoint: String,
  private val client: Lazy<OkHttpClient>,
) : TraceReporter {

  private val tracingService =
    Retrofit.Builder()
      .callFactory { client.value.newCall(it) }
      .addConverterFactory(WireConverterFactory.create())
      .baseUrl("https://example.com")
      .validateEagerly(true)
      .build()
      .create<TracingService>()

  override suspend fun sendTrace(spans: ListOfSpans) {
    tracingService.sendTrace(endpoint, spans)
  }
}
