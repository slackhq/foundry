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
package foundry.intellij.skate.tracing

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import foundry.intellij.skate.util.tracingEndpoint
import foundry.tracing.KeyValue
import foundry.tracing.ListOfSpans
import foundry.tracing.model.buildSpan
import foundry.tracing.model.makeId
import foundry.tracing.model.newTagBuilder
import foundry.tracing.reporter.SimpleTraceReporter
import foundry.tracing.reporter.TraceReporter
import foundry.tracing.reporter.TraceReporter.NoOpTraceReporter
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.Buffer
import okio.ByteString
import org.apache.http.HttpException

class SkateTraceReporter(val project: Project) : TraceReporter {

  private val delegate by lazy {
    val okHttpClient = lazy { OkHttpClient.Builder().build() }
    val loggingInterceptor = Interceptor { chain ->
      val request = chain.request()

      // Log request
      LOG.info("[TRACES API] --> ${request.method} ${request.url}")
      println("[TRACES API] --> ${request.method} ${request.url}")

      // Log request headers
      request.headers.forEach { (name, value) ->
        LOG.info("[TRACES API] $name: $value")
        println("[TRACES API] $name: $value")
      }

      // Log request body if present
      request.body?.let { body ->
        val buffer = Buffer()
        body.writeTo(buffer)
        val bodyString = buffer.readUtf8()
        LOG.info("[TRACES API] Request Body: $bodyString")
        println("[TRACES API] Request Body: $bodyString")
      }

      LOG.info("[TRACES API] --> END ${request.method}")
      println("[TRACES API] --> END ${request.method}")

      // Execute request
      val response = chain.proceed(request)

      // Log response
      LOG.info("[TRACES API] <-- ${response.code} ${response.message} ${response.request.url}")
      println("[TRACES API] <-- ${response.code} ${response.message} ${response.request.url}")

      // Log response headers
      response.headers.forEach { (name, value) ->
        LOG.info("[TRACES API] $name: $value")
        println("[TRACES API] $name: $value")
      }

      // Log response body if present
      response.body?.let { responseBody ->
        val source = responseBody.source()
        source.request(Long.MAX_VALUE)
        val buffer = source.buffer.clone()
        val bodyString = buffer.readUtf8()
        LOG.info("[TRACES API] Response Body: $bodyString")
        println("[TRACES API] Response Body: $bodyString")
      }

      LOG.info("[TRACES API] <-- END HTTP")
      println("[TRACES API] <-- END HTTP")

      response
    }

    val loggingClient = lazy {
      okHttpClient.value.newBuilder().addInterceptor(loggingInterceptor).build()
    }
    val endPoint = project.tracingEndpoint()
    if (endPoint != null) {
      LOG.info("[TRACES API] Traces endpoint configured: $endPoint")
      println("[TRACES API] Traces endpoint configured: $endPoint")
      SimpleTraceReporter(endPoint, loggingClient)
    } else {
      LOG.info("No endpoint set up for tracing")
      NoOpTraceReporter
    }
  }

  override suspend fun sendTrace(spans: ListOfSpans) {
    try {
      LOG.info("=== SKATE TRACE REQUEST ===")
      LOG.info("Endpoint: ${project.tracingEndpoint()}")
      LOG.info("Sending trace with ${spans.spans.size} span(s)")
      LOG.info("Trace ID: ${spans.spans.firstOrNull()?.trace_id}")
      LOG.info("Trace tags: ${spans.tags}")

      // Log detailed span information
      spans.spans.forEachIndexed { index, span ->
        LOG.info("  Span $index:")
        LOG.info("    Name: ${span.name}")
        LOG.info("    Duration: ${span.duration_micros} μs")
        LOG.info("    Tags: ${span.tags}")
      }
      LOG.info("Full trace data: $spans")
      LOG.info("===========================")

      // Also print to stdout for easy viewing
      println("=== SKATE TRACE REQUEST ===")
      println("Endpoint: ${project.tracingEndpoint()}")
      println("Sending trace with ${spans.spans.size} span(s)")
      println("Trace ID: ${spans.spans.firstOrNull()?.trace_id}")
      println("Trace tags: ${spans.tags}")

      spans.spans.forEachIndexed { index, span ->
        println("  Span $index:")
        println("    Name: ${span.name}")
        println("    Duration: ${span.duration_micros} μs")
        println("    Tags: ${span.tags}")
      }
      println("Full trace data: $spans")
      println("===========================")

      delegate.sendTrace(spans)

      LOG.info("=== SKATE TRACE RESPONSE ===")
      LOG.info("Trace sent successfully")
      LOG.info("============================")
      println("=== SKATE TRACE RESPONSE ===")
      println("Trace sent successfully")
      println("============================")
    } catch (httpException: HttpException) {
      LOG.error("=== SKATE TRACE ERROR ===")
      LOG.error("Uploading Skate plugin trace failed.", httpException)
      LOG.error("=========================")
      println("=== SKATE TRACE ERROR ===")
      println("ERROR sending trace: ${httpException.message}")
      println("Stack trace: ${httpException.stackTraceToString()}")
      println("=========================")
    } catch (e: Exception) {
      LOG.error("=== SKATE TRACE ERROR ===")
      LOG.error("Unexpected error sending trace", e)
      LOG.error("=========================")
      println("=== SKATE TRACE ERROR ===")
      println("Unexpected error: ${e.message}")
      println("Stack trace: ${e.stackTraceToString()}")
      println("=========================")
    }
  }

  fun createPluginUsageTraceAndSendTrace(
    spanName: String,
    startTimestamp: Instant,
    spanDataMap: List<KeyValue>,
    traceId: ByteString = makeId(),
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
          startTimestamp.toEpochMilli() * 1000, // Convert milliseconds to microseconds
        durationMicros =
          Duration.between(startTimestamp, Instant.now()).toMillis() * 1000, // Convert milliseconds to microseconds
        traceId = traceId,
        parentId = parentId,
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
    const val DATABASE_NAME: String = "traces_prod"
    private val LOG: Logger = Logger.getInstance(SkateTraceReporter::class.java)
  }
}
