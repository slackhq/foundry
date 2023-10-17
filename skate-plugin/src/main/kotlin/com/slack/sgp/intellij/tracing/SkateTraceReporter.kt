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
package com.slack.sgp.intellij.tracing

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.slack.sgp.tracing.KeyValue
import com.slack.sgp.tracing.ListOfSpans
import com.slack.sgp.tracing.model.buildSpan
import com.slack.sgp.tracing.model.tagBuilderImpl
import com.slack.sgp.tracing.reporter.SimpleTraceReporter
import com.slack.sgp.tracing.reporter.TraceReporter
import com.slack.sgp.tracing.reporter.TraceReporter.NoOpTraceReporter
import java.time.Duration
import java.time.Instant
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.apache.http.HttpException

class SkateTraceReporter(private val offline: Boolean = false) : TraceReporter {

  private val delegate by lazy {
    val okHttpClient = lazy { OkHttpClient.Builder().build() }
    val loggingInterceptor =
      HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }

    val loggingClient = lazy {
      okHttpClient.value.newBuilder().addInterceptor(loggingInterceptor).build()
    }
    if (offline) {
      NoOpTraceReporter
    } else {
      SimpleTraceReporter(TRACING_ENDPOINT, loggingClient)
    }
  }

  override suspend fun sendTrace(spans: ListOfSpans) {
    try {
      LOG.info(spans.toString())
      delegate.sendTrace(spans)
    } catch (httpException: HttpException) {
      LOG.error("Uploading Skate plugin trace failed.", httpException)
    }
  }

  fun createPluginUsageTraceAndSendTrace(
    spanName: String,
    startTimestamp: Instant,
    spanDataMap: List<KeyValue>
  ): ListOfSpans? {
    if (spanDataMap.isEmpty()) {
      LOG.debug("Skipping sending traces because span data is missing")
      return null
    }
    val traceTags =
      tagBuilderImpl().apply {
        "service_name" tagTo SERVICE_NAME
        "database" tagTo DATABASE_NAME
      }
    val span =
      buildSpan(
        name = spanName,
        startTimestampMicros =
          startTimestamp.toEpochMilli().toDuration(DurationUnit.MILLISECONDS).inWholeMicroseconds,
        durationMicros =
          Duration.between(startTimestamp, Instant.now())
            .toMillis()
            .toDuration(DurationUnit.MILLISECONDS)
            .inWholeMicroseconds
      ) {
        try {
          val skatePlugin = PluginManagerCore.getPlugin(PluginId.getId("com.slack.intellij.skate"))
          if (skatePlugin != null) "skate_version" tagTo skatePlugin.version
          "ide_version" tagTo ApplicationInfo.getInstance().fullVersion
        } catch (nullPointerException: NullPointerException) {
          LOG.warn("Failed to get IDE and Plugin version", nullPointerException)
        }
        "user" tagTo System.getenv("USER")
        this.addAll(spanDataMap)
      }
    val spans = ListOfSpans(spans = listOf(span), tags = traceTags)
    runBlocking { sendTrace(spans) }
    return spans
  }

  companion object {
    const val SERVICE_NAME: String = "lp_test_skate_plugin"
    const val DATABASE_NAME: String = "itools"
    const val TRACING_ENDPOINT: String = "https://slackb.com/traces/v1/list_of_spans/proto"
    private val LOG: Logger = Logger.getInstance(SkateTraceReporter::class.java)
  }
}
