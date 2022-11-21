/*
 * Copyright (C) 2022 Slack Technologies, LLC
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
package slack.gradle.util

import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import org.junit.Assert.fail
import org.junit.Test

class ThermlogParserTest {

  @Test
  fun happyPath() {
    val log = ThermlogParser.parse(UNTHROTTLED_EXAMPLE)
    check(log is ThermalsData)
    assertThat(log.wasThrottled).isFalse()
    assertThat(log.logs)
      .containsExactly(
        thermLog("2021-07-07 12:32:50 -0400", 100, 8, 100),
        thermLog("2021-07-07 12:32:50 -0400", 100, 8, 100),
        thermLog("2021-07-07 12:32:50 -0400", 100, 8, 100),
      )
  }

  @Test
  fun emptyLogs() {
    val log = ThermlogParser.parse("")
    check(log is Thermals.Empty)
    assertThat(log.wasThrottled).isFalse()
  }

  @Test
  fun throttled() {
    val log = ThermlogParser.parse(THROTTLED_EXAMPLE)
    check(log is ThermalsData)
    assertThat(log.wasThrottled).isTrue()
    assertThat(log.logs)
      .containsExactly(
        thermLog("2021-07-07 12:32:50 -0400", 100, 8, 90),
        thermLog("2021-07-07 12:32:50 -0400", 100, 8, 92),
        thermLog("2021-07-07 12:32:50 -0400", 100, 8, 100),
      )
  }

  @Test
  fun incompleteDataIsSkipped() {
    val log = ThermlogParser.parse(INCOMPLETE_EXAMPLE)
    check(log is ThermalsData)
    assertThat(log.wasThrottled).isFalse()
    assertThat(log.logs)
      .containsExactly(
        thermLog("2021-07-07 12:32:50 -0400", 100, 8, 100),
      )
  }

  @Test
  fun stats() {
    val speedLimits = listOf(100, 0, 50)
    val log = Thermals.create(speedLimits.map { ThermLog(LocalDateTime.now(), 100, 8, it) })
    check(log is ThermalsData)
    assertThat(log.average).isEqualTo(50)
    assertThat(log.percentThrottled).isEqualTo(66)
    assertThat(log.lowest).isEqualTo(0)
    assertThat(log.allSpeedLimits).isEqualTo(speedLimits)
  }

  @Test
  fun urls_minimum() {
    val speedLimits = (0..1000).toList()
    val log = Thermals.create(speedLimits.map { ThermLog(LocalDateTime.now(), 100, 8, it) })
    check(log is ThermalsData)
    try {
      log.chartUrl(100)
      fail()
    } catch (e: IllegalArgumentException) {
      assertThat(e).hasMessageThat().contains("is below the minimum")
    }
  }

  @Test
  fun urls_stress_test() {
    for (length in 200..1000) {
      val speedLimits = (0..length).toList()
      val log = Thermals.create(speedLimits.map { ThermLog(LocalDateTime.now(), 100, 8, it) })
      check(log is ThermalsData)
      val url1 = log.chartUrl(1000)
      assertThat(url1.length).isLessThan(1_001)
    }
  }

  @Test
  fun garbageDataIsGracefullySkipped() {
    val log = checkNotNull(ThermlogParser.parse(GARBAGE_DATA))
    check(log is ThermalsData)
    assertThat(log.logs).hasSize(2)
    assertThat(log.logs[0].speedLimit).isEqualTo(90)
    assertThat(log.logs[1].speedLimit).isEqualTo(100)
  }

  private fun thermLog(
    rawString: String,
    schedulerLimit: Int,
    availableCpus: Int,
    cpuLimit: Int
  ): ThermLog {
    return ThermLog(
      LocalDateTime.parse(rawString, ThermlogParser.TIMESTAMP_PATTERN),
      schedulerLimit,
      availableCpus,
      cpuLimit
    )
  }
}

private val THROTTLED_EXAMPLE =
  """
2021-07-07 12:32:50 -0400 CPU Power notify
	CPU_Scheduler_Limit 	= 100
	CPU_Available_CPUs 	= 8
	CPU_Speed_Limit 	= 90
2021-07-07 12:32:50 -0400 CPU Power notify
	CPU_Scheduler_Limit 	= 100
	CPU_Available_CPUs 	= 8
	CPU_Speed_Limit 	= 92

2021-07-07 12:32:50 -0400 CPU Power notify
	CPU_Scheduler_Limit 	= 100
	CPU_Available_CPUs 	= 8
	CPU_Speed_Limit 	= 100
"""
    .trimIndent()

/** Weird but actual phenomenon we've seen. */
private val GARBAGE_DATA =
  """
2021-07-07 12:32:50 -0400 CPU Power notify
	CPU_Scheduler_Limit 	= 100
	CPU_Available_CPUs 	= 8
	CPU_Speed_Limit 	= 90
2021-07-07 12:32:50 -0400 al;dkfjaskljdfhaklsjdhfklAJSDFLJAKSCPU Power notify
	CPU_Scheduler_Limit 	= 100
	CPU_Available_CPUs 	= 8
	CPU_Speed_Limit 	= 92

2021-07-07 12:32:50 -0400 CPU Power notify
	CPU_Scheduler_Limit 	= 100
	CPU_Available_CPUs 	= 8
	CPU_Speed_Limit 	= 100
"""
    .trimIndent()

private val UNTHROTTLED_EXAMPLE =
  """
2021-07-07 12:32:50 -0400 CPU Power notify
	CPU_Scheduler_Limit 	= 100
	CPU_Available_CPUs 	= 8
	CPU_Speed_Limit 	= 100
2021-07-07 12:32:50 -0400 CPU Power notify
	CPU_Scheduler_Limit 	= 100
	CPU_Available_CPUs 	= 8
	CPU_Speed_Limit 	= 100
2021-07-07 12:32:50 -0400 CPU Power notify
	CPU_Scheduler_Limit 	= 100
	CPU_Available_CPUs 	= 8
	CPU_Speed_Limit 	= 100
"""
    .trimIndent()

private val INCOMPLETE_EXAMPLE =
  """
2021-07-07 12:32:50 -0400 CPU Power notify
	CPU_Scheduler_Limit 	= 100
	CPU_Speed_Limit 	= 100
2021-07-07 12:32:50 -0400 CPU Power notify
	CPU_Scheduler_Limit 	= 100
	CPU_Available_CPUs 	= 8
	CPU_Speed_Limit 	= 100
2021-07-07 12:32:50 -0400 CPU Power notify
	CPU_Available_CPUs 	= 8
	CPU_Speed_Limit 	= 100
"""
    .trimIndent()
