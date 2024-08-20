package com.slack.gradle.develocity

import com.gabrielfeo.develocity.api.DevelocityApi
import com.gabrielfeo.develocity.api.extension.getBuildsFlow
import com.gabrielfeo.develocity.api.model.BuildModelName
import com.google.auto.service.AutoService
import com.jakewharton.picnic.TextAlignment
import com.jakewharton.picnic.table
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.toList
import okio.Path

@AutoService(DevelocityQuery::class)
internal class SlowestAverageBuildQuery : DevelocityQuery {
  override val name: String = "slowest-average-builds"

  override suspend fun run(api: DevelocityApi, outputDir: Path, since: Instant, limit: Int) {
    val builds = api.slowestAverageBuilds(since, limit)

    val table = table {
      cellStyle {
        border = true
        alignment = TextAlignment.MiddleLeft
        paddingLeft = 1
        paddingRight = 1
      }
      body {
        row {
          cell("Slowest average builds since $since") {
            columnSpan = 2
            alignment = TextAlignment.MiddleCenter
          }
        }
        row {
          cell("Name") { alignment = TextAlignment.MiddleCenter }
          cell("Average Time") { alignment = TextAlignment.MiddleCenter }
        }
        for ((name, avg) in builds) {
          row {
            cell(name) { alignment = TextAlignment.MiddleCenter }
            cell(avg.minutesAndSecondsString) { alignment = TextAlignment.MiddleCenter }
          }
        }
      }
    }
  }

  private suspend fun DevelocityApi.slowestAverageBuilds(
    since: Instant,
    limit: Int,
  ): Map<String, Duration> {
    val builds =
      buildsApi
        .getBuildsFlow(
          0,
          fromInstant = since.toEpochMilli(),
          models = listOf(BuildModelName.gradleAttributes),
        )
        .commonLocalBuilds()
        .toList()
        .groupBy { it.environment.username!! }
        .mapValues { (_, attrs) ->
          Duration.ofMillis(attrs.map { it.buildDuration }.average().roundToLong())
        }
        .entries
        .sortedByDescending { it.value }
        .take(limit)
        .associate { (name, avg) -> name to avg }

    return builds
  }
}
