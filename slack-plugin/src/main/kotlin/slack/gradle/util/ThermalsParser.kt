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

import com.sun.jna.Library
import com.sun.jna.Native
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.jvm.Throws
import slack.gradle.util.charting.ChartCreator

/** Utility for parsing thermals as reported by `pmset -g thermlog`. */
internal object ThermalsParser {
  internal val TIMESTAMP_PATTERN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z")

  internal fun parse(log: String): Thermals {
    return parseLogs(log)?.let(Thermals.Companion::create) ?: Thermals.Empty
  }

  private fun parseLogs(log: String): List<ThermLog>? {
    val logs = mutableListOf<ThermLog>()
    var currentTime: LocalDateTime? = null
    var currentScheduler: Int? = null
    var availableCpus: Int? = null
    var speedLimit: Int? = null

    fun flush() {
      val prevCurrentTime = currentTime
      val prevCurrentScheduler = currentScheduler
      val prevAvailableCpus = availableCpus
      val prevSpeedLimit = speedLimit
      if (prevCurrentTime != null &&
          prevCurrentScheduler != null &&
          prevAvailableCpus != null &&
          prevSpeedLimit != null
      ) {
        logs +=
          ThermLog(
            prevCurrentTime,
            prevCurrentScheduler,
            prevAvailableCpus,
            prevSpeedLimit,
          )
      }
    }

    for (line in log.lineSequence()) {
      when {
        line.isBlank() -> continue
        "CPU Power notify" in line -> {
          // Flush the previous data
          flush()
          currentTime = null
          currentScheduler = null
          availableCpus = null
          speedLimit = null
          val localCurrentTime =
            try {
              parseTimestamp(line)
            } catch (e: DateTimeParseException) {
              // Sometimes the process falls over piping data out, so gracefully skip this one
              null
            }
          currentTime = localCurrentTime
        }
        currentTime != null && currentScheduler == null && "CPU_Scheduler_Limit" in line -> {
          currentScheduler = parseDiagnostic(line)
        }
        currentTime != null && availableCpus == null && "CPU_Available_CPUs" in line -> {
          availableCpus = parseDiagnostic(line)
        }
        currentTime != null && speedLimit == null && "CPU_Speed_Limit" in line -> {
          speedLimit = parseDiagnostic(line)
        }
        else -> {
          // Skip it, unrecognized text
        }
      }
    }

    // flush any remaining
    flush()
    return logs.takeIf { it.isNotEmpty() }
  }

  private fun parseDiagnostic(line: String): Int {
    // CPU_Available_CPUs 	= 8
    return line.substringAfter("=").trim().toInt()
  }

  @Throws(DateTimeParseException::class)
  private fun parseTimestamp(line: String): LocalDateTime {
    // 2021-07-07 12:32:50 -0400 CPU Power notify
    return LocalDateTime.parse(line.substringBefore("CPU").trim(), TIMESTAMP_PATTERN)
  }
}

public sealed class Thermals {
  public abstract val wasThrottled: Boolean

  public object Empty : Thermals() {
    override val wasThrottled: Boolean = false
  }

  internal companion object {
    fun create(logs: List<ThermLog>): Thermals {
      return if (logs.isEmpty()) {
        Empty
      } else {
        ThermalsData(logs)
      }
    }
  }
}

public data class ThermalsData(public val logs: List<ThermLog>) : Thermals() {

  init {
    check(logs.isNotEmpty())
  }

  override val wasThrottled: Boolean by lazy { logs.any { it.isThrottled } }

  public val lowest: Int by lazy { logs.minOf { it.speedLimit } }

  public val average: Int by lazy { logs.sumOf { it.speedLimit } / logs.size }

  public val percentThrottled: Int by lazy {
    ((logs.count { it.speedLimit < 100 }.toDouble() / logs.size) * 100).toInt()
  }

  public val allSpeedLimits: List<Int> by lazy { logs.map { it.speedLimit } }

  /** Returns a url to a chart representation of the thermals logged. */
  public fun chartUrl(urlCharLimit: Int): String {
    require(urlCharLimit >= MINIMUM_CHAR_LIMIT) {
      "Input limit $urlCharLimit is below the minimum of $MINIMUM_CHAR_LIMIT."
    }
    // x axis is the duration of the build in seconds
    val xLength = Duration.between(logs.first().timestamp, logs.last().timestamp).seconds.toInt()
    val xRange = 0..xLength
    val yRange = 0..100

    return generateSequence(1) { it + 1 }
      .map { sampleSize ->
        val sampleValues = allSpeedLimits.sample(sampleSize)

        // The data is sampled but the x axis should still use labels for the full range of time.
        ChartCreator.createChartUrl(data = sampleValues, xRange = xRange, yRange = yRange)
      }
      .first { it.length < urlCharLimit }
  }

  private companion object {
    /** Keep the minimum relatively high to avoid work in sampling data. */
    const val MINIMUM_CHAR_LIMIT = 1000

    fun List<Int>.sample(sampleSize: Int): List<Int> {
      return filterIndexed { index, _ -> index % sampleSize == 0 }
    }
  }
}

public data class ThermLog(
  public val timestamp: LocalDateTime,
  public val schedulerLimit: Int,
  public val availableCpus: Int,
  public val speedLimit: Int,
) {
  val isThrottled: Boolean = speedLimit != -1 && speedLimit < 100
}

internal object AppleSiliconThermals {
  fun getThermals(): ThermalState? {
    val rawValue = JnaThermalState.INSTANCE.thermal_state()
    // Raw value in this case is 0-4, which we use as the ordinal of our enums for convenience
    return if (rawValue in 0..ThermalState.values().size) {
      ThermalState.values()[rawValue]
    } else {
      null
    }
  }

  /** From `Foundation.NSProcessInfo.ThermalState`. */
  enum class ThermalState(@Suppress("unused") val rawValue: Int) {
    /** No corrective action is needed. */
    NOMINAL(0),

    /**
     * The system has reached a state where fans may become audible (on systems which have fans).
     * Recommendation: Defer non-user-visible activity.
     */
    FAIR(1),

    /**
     * Fans are running at maximum speed (on systems which have fans), system performance may be
     * impacted. Recommendation: reduce application's usage of CPU, GPU and I/O, if possible. Switch
     * to lower quality visual effects, reduce frame rates.
     */
    SERIOUS(2),

    /**
     * System performance is significantly impacted and the system needs to cool down.
     * Recommendation: reduce application's usage of CPU, GPU, and I/O to the minimum level needed
     * to respond to user actions. Consider stopping use of camera and other peripherals if your
     * application is using them.
     */
    CRITICAL(3),
  }
}

/** JNA wrapper interface for the `thermal_state` dylib */
internal interface JnaThermalState : Library {
  // Corresponds to the thermal_state function in thermal_state.swift
  @Suppress("FunctionName") fun thermal_state(): Int

  companion object {
    val INSTANCE: JnaThermalState =
      Native.load("thermal_state", JnaThermalState::class.java) as JnaThermalState
  }
}
