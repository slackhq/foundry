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
package foundry.cli.sarif

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.NullableOption
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import io.github.detekt.sarif4k.Level
import io.github.detekt.sarif4k.Result
import io.github.detekt.sarif4k.SarifSchema210
import io.github.detekt.sarif4k.Suppression
import io.github.detekt.sarif4k.SuppressionKind

internal val BASELINE_SUPPRESSION: Suppression =
  Suppression(
    kind = SuppressionKind.External,
    justification = "This issue was suppressed by the baseline",
  )

/**
 * A comparator used to sort instances of the Result class.
 *
 * The comparison is done based on the following properties in the given order:
 * - ruleIndex
 * - ruleID
 * - uri of the first physical location's artifact location
 * - startLine of the first physical location's region
 * - startColumn of the first physical location's region
 * - endLine of the first physical location's region
 * - endColumn of the first physical location's region
 * - text of the message
 */
internal val RESULT_SORT_COMPARATOR =
  compareBy<Result>(
    { it.ruleIndex },
    { it.ruleID },
    { it.locations?.firstOrNull()?.physicalLocation?.artifactLocation?.uri },
    { it.locations?.firstOrNull()?.physicalLocation?.region?.startLine },
    { it.locations?.firstOrNull()?.physicalLocation?.region?.startColumn },
    { it.locations?.firstOrNull()?.physicalLocation?.region?.endLine },
    { it.locations?.firstOrNull()?.physicalLocation?.region?.endColumn },
    { it.message.text },
  )

/**
 * Identifies a [Result] by its rule and first location. Compared by value rather than by hash code
 * so two unrelated results can never be treated as the same result.
 */
internal data class ResultIdentityKey(
  val ruleID: String?,
  val uri: String?,
  val startLine: Long?,
  val startColumn: Long?,
  val endLine: Long?,
  val endColumn: Long?,
)

/** Identifies a [Result] by its [ResultIdentityKey] plus its message text. */
internal data class ResultShallowKey(val identity: ResultIdentityKey, val messageText: String?)

/**
 * Returns the identity key for the [Result] object. This matches results that point to the same
 * issue+location, but not necessarily the same [Result.level]/[Result.message].
 */
internal val Result.identityKey: ResultIdentityKey
  get() {
    val physicalLocation = locations?.firstOrNull()?.physicalLocation
    val region = physicalLocation?.region
    return ResultIdentityKey(
      ruleID = ruleID,
      uri = physicalLocation?.artifactLocation?.uri,
      startLine = region?.startLine,
      startColumn = region?.startColumn,
      endLine = region?.endLine,
      endColumn = region?.endColumn,
    )
  }

/**
 * Returns the shallow key for the [Result] object. This includes the [identityKey] but also
 * differentiates results whose [Result.message] is different.
 */
internal val Result.shallowKey: ResultShallowKey
  get() = ResultShallowKey(identityKey, message.text)

private val LEVEL_NAMES =
  Level.entries.joinToString(separator = ", ", prefix = "[", postfix = "]", transform = Level::name)

internal fun CliktCommand.levelOption(): NullableOption<Level, Level> {
  return option(
      "--level",
      "-l",
      help = "Priority level. Defaults to Error. Options are $LEVEL_NAMES",
    )
    .enum<Level>()
}

internal fun SarifSchema210.mergeWith(
  other: SarifSchema210,
  levelOverride: Level? = null,
  removeUriPrefixes: Boolean = false,
  log: (String) -> Unit,
): SarifSchema210 {
  return listOf(this, other).merge(levelOverride, removeUriPrefixes, log)
}

internal fun List<SarifSchema210>.merge(
  levelOverride: Level? = null,
  removeUriPrefixes: Boolean = false,
  log: (String) -> Unit,
): SarifSchema210 {
  check(isNotEmpty()) { "Must have at least one sarif file to merge!" }

  log("Merging $size sarif files")
  val mergedResults = flatMap {
    it.runs.single().results.orEmpty()
  }
    // Some projects produce multiple reports for different variants, so we need to
    // de-dupe.
    // Using the default distinct() function leaves duplicates, so using a custom selector
    .distinctBy { it.shallowKey }
    .also { log("Merged ${it.size} results") }

  if (mergedResults.isEmpty()) {
    // Nothing to do here, just return the first
    return this[0]
  }

  val sortedMergedRules = flatMap {
    it.runs.single().tool.driver.rules.orEmpty()
  }
    .associateBy { it.id }
    .toSortedMap()

  // Update rule.ruleIndex to match the index in rulesToAdd
  val ruleIndicesById =
    sortedMergedRules.entries.withIndex().associate { (index, entry) -> entry.key to index }
  val correctedResults =
    mergedResults
      .map { result ->
        val ruleId = result.ruleID
        val ruleIndex = ruleIndicesById.getValue(ruleId)
        result.copy(ruleIndex = ruleIndex.toLong())
      }
      .map {
        if (levelOverride != null) {
          it.copy(level = levelOverride)
        } else {
          it
        }
      }
      .sortedWith(RESULT_SORT_COMPARATOR)

  val sarifToUse =
    if (removeUriPrefixes) {
      // Just use the first if we don't care about originalUriBaseIDs
      first()
    } else {
      // Pick a sarif file to use as the base for the merged sarif file. We want one that has an
      // `originalURIBaseIDS` too since parsing possibly uses this.
      find { it.runs.firstOrNull()?.originalURIBaseIDS?.isNotEmpty() == true }
        ?: error("No sarif files had originalURIBaseIDS set, can't merge")
    }

  // Note: we don't sort these results by anything currently (location, etc), but maybe some day
  // we should if it matters for caching
  val runToCopy = sarifToUse.runs.single()
  val mergedTool =
    runToCopy.tool.copy(
      driver = runToCopy.tool.driver.copy(rules = sortedMergedRules.values.toList())
    )

  return sarifToUse.copy(
    runs = listOf(runToCopy.copy(tool = mergedTool, results = correctedResults))
  )
}
