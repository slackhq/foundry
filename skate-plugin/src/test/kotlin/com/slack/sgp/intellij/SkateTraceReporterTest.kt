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
package com.slack.sgp.intellij

import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.slack.sgp.intellij.tracing.SkateTraceReporter
import com.slack.sgp.intellij.tracing.SkateTraceReporter.Companion.DATABASE_NAME
import com.slack.sgp.intellij.tracing.SkateTraceReporter.Companion.SERVICE_NAME
import com.slack.sgp.tracing.KeyValue
import com.slack.sgp.tracing.ValueType
import com.slack.sgp.tracing.model.tagBuilderImpl
import java.time.Instant

class SkateTraceReporterTest : BasePlatformTestCase() {
  fun testSpanCreatedWithCorrectTags() {
    val traceTags =
      tagBuilderImpl().apply {
        "test_string" tagTo "test_value"
        "test_boolean" tagTo true
        "test_long" tagTo 10000000L
        "test_double" tagTo "1.25"
      }

    val listOfSpans =
      SkateTraceReporter(project, offline = true)
        .createPluginUsageTraceAndSendTrace(
          "fake_span_name",
          Instant.now(),
          traceTags,
          "Studio Giraffe",
          "0.2.0"
        )
    val expectedTags =
      mutableListOf(
        KeyValue("service_name", ValueType.STRING, SERVICE_NAME),
        KeyValue("database", ValueType.STRING, DATABASE_NAME)
      )

    val expectedSpanTags = tagBuilderImpl().apply {
      "skate_version" tagTo "0.2.0"
      "ide_version" tagTo "Studio Giraffe"
      "user" tagTo System.getenv("USER")
      "project_name" tagTo project.name
      addAll(traceTags)
    }

    assertThat(listOfSpans).isNotNull()
    if (listOfSpans != null) {
      assertThat(listOfSpans.tags).isEqualTo(expectedTags)
      assertThat(listOfSpans.spans.first().tags).isEqualTo(expectedSpanTags.toList())
    }
  }

  fun testSpanNotCreatedWhenSpanDataIsEmpty() {
    val listOfSpans =
      SkateTraceReporter(project, offline = true)
        .createPluginUsageTraceAndSendTrace(
          "fake_span_name",
          Instant.now(),
          tagBuilderImpl(),
          "Studio Giraffe",
          "0.2.0"
        )
    assertThat(listOfSpans).isNull()
  }

  fun testSpanNotCreatedWhenIdeVersionEmpty() {
    val listOfSpans =
      SkateTraceReporter(project, offline = true)
        .createPluginUsageTraceAndSendTrace(
          "fake_span_name",
          Instant.now(),
          tagBuilderImpl(),
          "Studio Giraffe",
          ""
        )
    assertThat(listOfSpans).isNull()
  }
}
