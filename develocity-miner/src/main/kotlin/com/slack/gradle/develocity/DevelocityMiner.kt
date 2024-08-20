package com.slack.gradle.develocity

import com.gabrielfeo.develocity.api.Config
import com.gabrielfeo.develocity.api.DevelocityApi
import com.gabrielfeo.develocity.api.extension.getBuildsFlow
import com.gabrielfeo.develocity.api.model.Build
import com.gabrielfeo.develocity.api.model.BuildModelName
import com.gabrielfeo.develocity.api.model.GradleAttributes
import com.gabrielfeo.develocity.api.model.GradleBuildCachePerformance
import com.github.ajalt.mordant.animation.progressAnimation
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Spinner
import com.jakewharton.picnic.Table
import com.jakewharton.picnic.TextAlignment
import com.jakewharton.picnic.table
import java.time.Instant
import java.util.ServiceLoader
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaDuration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import okio.FileSystem
import okio.Path

private fun loadQueries(): Map<String, DevelocityQuery> {
  val queries =
    ServiceLoader.load(DevelocityQuery::class.java, DevelocityQuery::class.java.classLoader)
  return queries.associateBy { it.name }
}

/**
 * TODO
 * - report formats
 * - query filtering
 */
internal class DevelocityMiner(
  val apiUrl: String,
  val apiToken: String,
  // Null means no caching
  val apiCache: Path?,
  val requestedQueries: Set<String>,
  val reportsPath: Path? = null,
  val since: Instant = Instant.now().minus(7.days.toJavaDuration()),
  val logLevel: String = "off",
  val fs: FileSystem = FileSystem.SYSTEM,
) {
  val queries: Map<String, DevelocityQuery> = loadQueries().filterKeys { it in requestedQueries }

  suspend fun run() {
    val config =
      Config(
        logLevel = logLevel,
        apiUrl = apiUrl,
        apiToken = { apiToken },
        cacheConfig =
          Config.CacheConfig(
            cacheEnabled = apiCache != null,
            cacheDir =
              ((apiCache ?: (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / ".develocity-api-cache")))
                .toFile(),
          ),
      )
    val api = DevelocityApi.newInstance(config)
    val t = Terminal()
    val reportsOutput =
      ((reportsPath ?: (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / ".develocity-api-reports")))
    fs.createDirectories(reportsOutput)
    try {
      // TODO multiple queries in parallel, update terminal UI
      //      queries.entries.forEach { (name, query) ->
      //        val path = reportsOutput / "$name.txt"
      //        fs.createDirectory(path)
      //        t.runLoad("Running query $name...") { query.run(api, path, since) }
      //      }

      t.runLoad("Loading average task times") {
        api.taskTimes(
          since = Instant.now().minus(2.days.toJavaDuration()),
          partitionPoint = Instant.now().minus(1.days.toJavaDuration()),
          tasks = setOf(":app:instrumentation-tests:shared:kaptInternalDebugKotlin"),
        )
      }

      //  t.runLoad("Loading slowest local builds...") {
      //    api.slowestLocalBuilds(Instant.now().minus(Duration.ofDays(1)))
      //  }
      //
      //  t.runLoad("Loading highest negative avoidance savings...") {
      //    api.highestNegativeAvoidanceSavings(Instant.now().minus(Duration.ofDays(1)))
      //  }
      //  t.runLoad("Loading users with most build cache errors...") {
      //    api.userWithMostBuildCacheErrors(Instant.now().minus(Duration.ofDays(6)))
      //  }

      //      t.runLoad("Loading slowest average gradle syncs...") {
      //        api.slowestAverageSyncs(Instant.now().minus(Duration.ofDays(1)))
      //      }
    } finally {
      api.shutdown()
    }
  }
}

private suspend fun Terminal.runLoad(message: String, block: suspend () -> Any) {
  val animation = progressAnimation {
    spinner(Spinner.Dots(TextColors.brightBlue))
    text(message)
  }
  animation.start()
  val result = block()
  animation.stop()
  rawPrint("\n\n")
  rawPrint(result.toString())
  rawPrint("\n\n")
}

internal fun Flow<GradleAttributes>.localBuilds(): Flow<GradleAttributes> = filter {
  "LOCAL" in it.tags
}

@JvmName("ciBuildsAttributes")
internal fun Flow<GradleAttributes>.ciBuilds(): Flow<GradleAttributes> = filter { "CI" in it.tags }

internal fun Flow<Build>.ciBuilds(): Flow<Build> = filter { "CI" in it.attributes.tags }

internal fun Flow<GradleAttributes>.ignoreIdeSync(): Flow<GradleAttributes> = filter {
  "IDE sync" !in it.tags
}

internal fun Flow<GradleAttributes>.ideSyncs(): Flow<GradleAttributes> = filter {
  "IDE sync" in it.tags
}

internal fun Flow<GradleAttributes>.ignoreTestTasks(): Flow<GradleAttributes> = filter {
  ":app:instrumentation-tests:shared:connectedInternalDebugAndroidTest" !in it.requestedTasks
}

internal val Build.attributes: GradleAttributes
  get() = models!!.gradleAttributes!!.model!!

internal val Build.gradleBuildCachePerformance: GradleBuildCachePerformance
  get() = models!!.gradleBuildCachePerformance!!.model!!

internal fun Flow<Build>.attributes(): Flow<GradleAttributes> = map { it.attributes }

internal fun Flow<Build>.commonLocalBuilds(): Flow<GradleAttributes> =
  attributes().localBuilds().ignoreIdeSync().ignoreTestTasks()

internal fun Flow<Build>.localSyncs(): Flow<GradleAttributes> =
  attributes().localBuilds().ideSyncs()

internal suspend fun DevelocityApi.slowestLocalBuilds(since: Instant): Table {
  val builds =
    buildsApi
      .getBuildsFlow(
        0,
        fromInstant = since.toEpochMilli(),
        models = listOf(BuildModelName.gradleAttributes),
      )
      .commonLocalBuilds()
      .toList()
      .sortedByDescending { attrs -> attrs.buildDuration }
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
        cell("Slowest builds since $since") {
          columnSpan = 4
          alignment = TextAlignment.MiddleCenter
        }
      }
      row {
        cell("Name") { alignment = TextAlignment.MiddleCenter }
        cell("Time") { alignment = TextAlignment.MiddleCenter }
        cell("Tasks") { alignment = TextAlignment.MiddleCenter }
        cell("URL") { alignment = TextAlignment.MiddleCenter }
      }
      for (build in builds) {
        row {
          cell(build.environment.username) { alignment = TextAlignment.MiddleCenter }
          cell(build.durationText) { alignment = TextAlignment.MiddleCenter }
          cell(build.requestedTasks) { alignment = TextAlignment.MiddleCenter }
          cell("https://gradle-enterprise.tinyspeck.com/s/${build.id}") {
            alignment = TextAlignment.MiddleCenter
          }
        }
      }
    }
  }
}
