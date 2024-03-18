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
import com.intellij.openapi.project.Project
import com.slack.sgp.intellij.util.tracingEndpoint
import com.slack.sgp.tracing.KeyValue
import com.slack.sgp.tracing.ListOfSpans
import com.slack.sgp.tracing.model.buildSpan
import com.slack.sgp.tracing.model.newTagBuilder
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
import okio.ByteString
import org.apache.http.HttpException

class SkateTraceReporter(val project: Project) : TraceReporter {

  private val delegate by lazy {
    val okHttpClient = lazy { OkHttpClient.Builder().build() }
    val loggingInterceptor =
      HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }

    val loggingClient = lazy {
      okHttpClient.value.newBuilder().addInterceptor(loggingInterceptor).build()
    }
    val endPoint = project.tracingEndpoint()
    if (endPoint != null) {
      SimpleTraceReporter(endPoint, loggingClient)
    } else {
      LOG.info("No endpoint set up for tracing")
      NoOpTraceReporter
    }
  }

  override suspend fun sendTrace(spans: ListOfSpans) {
    try {
      delegate.sendTrace(spans)
      LOG.info(spans.toString())
    } catch (httpException: HttpException) {
      LOG.error("Uploading Skate plugin trace failed.", httpException)
    }
  }

  fun createPluginUsageTraceAndSendTrace(
    spanName: String,
    startTimestamp: Instant,
    spanDataMap: List<KeyValue>,
    parentId: ByteString = ByteString.EMPTY,
    ideVersion: String = ApplicationInfo.getInstance().fullVersion,
    skatePluginVersion: String? =
      PluginManagerCore.getPlugin(PluginId.getId("com.slack.intellij.skate"))?.version,
  ): ListOfSpans? {
    if (spanDataMap.isEmpty() || skatePluginVersion.isNullOrBlank()) {
      return null
    }
    val traceTags =
      newTagBuilder().apply {
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
            .inWholeMicroseconds,
        parentId = parentId
      ) {
        "skate_version" tagTo skatePluginVersion
        "ide_version" tagTo ideVersion
        System.getenv("USER").takeUnless { it.isBlank() }?.let { "user" tagTo it }
        "project_name" tagTo project.name
        this.addAll(spanDataMap)
      }
    val spans = ListOfSpans(spans = listOf(span), tags = traceTags)
    runBlocking { sendTrace(spans) }
    return spans
  }

  companion object {
    const val SERVICE_NAME: String = "skate_plugin"
    const val DATABASE_NAME: String = "itools"
    private val LOG: Logger = Logger.getInstance(SkateTraceReporter::class.java)
  }
}
