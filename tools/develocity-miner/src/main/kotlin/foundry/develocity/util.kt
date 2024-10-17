package foundry.develocity

import com.gabrielfeo.develocity.api.DevelocityApi
import com.gabrielfeo.develocity.api.extension.getBuildsFlow
import com.gabrielfeo.develocity.api.model.BuildModelName
import com.gabrielfeo.develocity.api.model.GradleAttributes
import com.jakewharton.picnic.Table
import com.jakewharton.picnic.TextAlignment
import com.jakewharton.picnic.table
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList

internal suspend fun DevelocityApi.highestNegativeAvoidanceSavings(since: Instant): Table {
  val highestNegativeAvoidance =
    buildsApi
      .getBuildsFlow(
        0,
        fromInstant = since.toEpochMilli(),
        models = listOf(BuildModelName.gradleAttributes),
      )
      .commonLocalBuilds()
      .flatMapMerge { flow { emit(buildsApi.getGradleBuildCachePerformance(it.id)) } }
      .toList()
      .flatMap { it.taskExecution.filter { (it.avoidanceSavings ?: 0) < 0 } }
      .groupBy { it.taskPath }
      .map { (taskPath, taskExecutions) ->
        taskPath to taskExecutions.map { it.avoidanceSavings!! }.average()
      }
      .filterNot { it.first.contains("spotless") }
      // Not by descending because these are all negative numbers
      .sortedBy { it.second }
      .take(50)

  return table {
    cellStyle {
      border = true
      alignment = TextAlignment.MiddleLeft
      paddingLeft = 1
      paddingRight = 1
    }
    body {
      row {
        cell("Highest negative avoidance savings since $since") {
          columnSpan = 2
          alignment = TextAlignment.MiddleCenter
        }
      }
      row {
        cell("Task Path") { alignment = TextAlignment.MiddleCenter }
        cell("Average Avoidance Savings") { alignment = TextAlignment.MiddleCenter }
      }
      for ((taskPath, avg) in highestNegativeAvoidance) {
        row {
          cell(taskPath) { alignment = TextAlignment.MiddleCenter }
          cell("${(avg / 1000)}s") { alignment = TextAlignment.MiddleCenter }
        }
      }
    }
  }
}

internal suspend fun DevelocityApi.userWithMostBuildCacheErrors(since: Instant): Table {
  val results =
    buildsApi
      .getBuildsFlow(
        0,
        fromInstant = since.toEpochMilli(),
        models = listOf(BuildModelName.gradleAttributes),
      )
      .commonLocalBuilds()
      .map { it to buildsApi.getGradleBuildCachePerformance(it.id) }
      .toList()
      .groupBy { it.first.environment.username!! }
      .map { (username, buildPairs) ->
        username to
          buildPairs.count { it.second.buildCaches?.remote?.isDisabledDueToError ?: false }
      }
      .filter { it.second > 0 }
      .sortedByDescending { it.second }

  return table {
    cellStyle {
      border = true
      alignment = TextAlignment.MiddleLeft
      paddingLeft = 1
      paddingRight = 1
    }
    body {
      row {
        cell("Users with most build cache errors since $since") {
          columnSpan = 2
          alignment = TextAlignment.MiddleCenter
        }
      }
      row {
        cell("Username") { alignment = TextAlignment.MiddleCenter }
        cell("Total builds disabled due to errors") { alignment = TextAlignment.MiddleCenter }
      }
      for ((username, count) in results) {
        row {
          cell(username) { alignment = TextAlignment.MiddleCenter }
          cell(count) { alignment = TextAlignment.MiddleCenter }
        }
      }
    }
  }
}

internal suspend fun DevelocityApi.slowestAverageSyncs(since: Instant): Table {
  val builds =
    buildsApi
      .getBuildsFlow(
        0,
        fromInstant = since.toEpochMilli(),
        models = listOf(BuildModelName.gradleAttributes),
      )
      .localSyncs()
      .toList()
      .groupBy { it.environment.username!! }
      .mapValues { (_, attrs) ->
        Duration.ofMillis(attrs.map { it.buildDuration }.average().roundToLong())
      }
      .entries
      .sortedByDescending { it.value }
      .take(10)

  return table {
    cellStyle {
      border = true
      alignment = TextAlignment.MiddleLeft
      paddingLeft = 1
      paddingRight = 1
    }
    body {
      row {
        cell("Slowest average Gradle syncs since $since") {
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

internal suspend fun DevelocityApi.taskTimes(
  since: Instant,
  partitionPoint: Instant,
  tasks: Set<String>,
): Table {
  val (beforeAverages, afterAverages) =
    buildsApi
      .getBuildsFlow(
        fromInstant = since.toEpochMilli(),
        models =
          listOf(BuildModelName.gradleAttributes, BuildModelName.gradleBuildCachePerformance),
        query =
          """
          -tag:CI
        """
            .trimIndent(),
      )
      .onEach { println("Received ${it.id}") }
      .onEmpty { error("No builds found since $since with the requested tasks.") }
      //      .ciBuilds()
      .take(10)
      .toList()
      .partition { Instant.ofEpochMilli(it.attributes.buildStartTime) < partitionPoint }
      .let { (before, after) ->
        val beforeAverages =
          before
            .flatMap { build -> build.gradleBuildCachePerformance.taskExecution }
            .filter { it.taskPath in tasks && it.avoidanceOutcome.value.contains("executed_") }
            .groupBy { it.taskPath }
            .mapValues { (_, taskExecutions) ->
              taskExecutions.map { it.duration }.average().milliseconds.toJavaDuration()
            }
        val afterAverages =
          after
            .flatMap { build -> build.gradleBuildCachePerformance.taskExecution }
            .filter { it.taskPath in tasks }
            .groupBy { it.taskPath }
            .mapValues { (_, taskExecutions) ->
              taskExecutions.map { it.duration }.average().milliseconds.toJavaDuration()
            }
        beforeAverages to afterAverages
      }

  return table {
    cellStyle {
      border = true
      alignment = TextAlignment.MiddleLeft
      paddingLeft = 1
      paddingRight = 1
    }
    body {
      row {
        cell("Average task times since $since (before and after $partitionPoint)") {
          columnSpan = 3
          alignment = TextAlignment.MiddleCenter
        }
      }
      row {
        cell("Task") { alignment = TextAlignment.MiddleCenter }
        cell("Before") { alignment = TextAlignment.MiddleCenter }
        cell("After") { alignment = TextAlignment.MiddleCenter }
      }
      for ((task, before) in beforeAverages.toSortedMap()) {
        row {
          cell(task) { alignment = TextAlignment.MiddleCenter }
          cell(before.minutesAndSecondsString) { alignment = TextAlignment.MiddleCenter }
          cell(afterAverages.getValue(task).minutesAndSecondsString) {
            alignment = TextAlignment.MiddleCenter
          }
        }
      }
    }
  }
}

internal val Duration.minutesAndSecondsString: String
  get() {
    val minutes = toMinutesPart()
    val seconds = toSecondsPart()
    val secondPadding = if (seconds < 10) "0" else ""
    return "$minutes:$secondPadding$seconds"
  }

internal val GradleAttributes.durationText
  get() = Duration.ofMillis(buildDuration).minutesAndSecondsString
