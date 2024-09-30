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
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.path
import com.google.auto.service.AutoService
import foundry.cli.CommandFactory
import io.github.detekt.sarif4k.BaselineState
import io.github.detekt.sarif4k.SarifSchema210
import io.github.detekt.sarif4k.SarifSerializer
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.system.exitProcess

/** A CLI that applies baselines data to a SARIF file. See the docs on [Mode] for more details. */
public class ApplyBaselinesToSarifs : CliktCommand() {

  @AutoService(foundry.cli.CommandFactory::class)
  public class Factory : foundry.cli.CommandFactory {
    override val key: String = "apply-baselines-to-sarifs"
    override val description: String = DESCRIPTION

    override fun create(): CliktCommand = ApplyBaselinesToSarifs()
  }

  private companion object {
    const val DESCRIPTION = "A CLI that applies baselines data to a SARIF file."
  }

  override fun help(context: Context): String = DESCRIPTION

  private val baseline by
    option("--baseline", "-b", help = "The baseline SARIF file to use.")
      .path(mustExist = true, canBeDir = false)
      .required()

  private val current by
    option("--current", "-c", help = "The SARIF file to apply the baseline to.")
      .path(mustExist = true, canBeDir = false)
      .required()

  private val output by
    option("--output", "-o", help = "The output SARIF file to write.")
      .path(canBeDir = false)
      .required()

  private val removeUriPrefixes by
    option(
        "--remove-uri-prefixes",
        help =
          "When enabled, removes the root project directory from location uris such that they are only " +
            "relative to the root project dir.",
      )
      .flag()

  private val mode by
    option("--mode", "-m", help = "The mode to run in.").enum<Mode>(ignoreCase = true).required()

  private val includeAbsent by
    option("--include-absent", "-a", help = "Include absent results in updating.").flag()

  override fun run() {
    if (includeAbsent && mode != Mode.UPDATE) {
      echo("--include-absent can only be used with --mode=update", err = true)
      exitProcess(1)
    }
    val baseline = SarifSerializer.fromJson(baseline.readText())
    val sarifToUpdate = SarifSerializer.fromJson(current.readText())

    val updatedSarif = sarifToUpdate.applyBaseline(baseline)

    output.writeText(SarifSerializer.toJson(updatedSarif))
  }

  @Suppress("LongMethod")
  private fun SarifSchema210.applyBaseline(baseline: SarifSchema210): SarifSchema210 {
    // Assume a single run for now
    val results = runs.first().results!!
    val baselineResults = baseline.runs.first().results!!

    val suppressions = listOf(BASELINE_SUPPRESSION)

    return when (mode) {
      Mode.MERGE -> {
        // Mark baselines as suppressed and no baseline state
        val suppressedBaselineSchema =
          baseline.copy(
            runs =
              baseline.runs.map { run ->
                run.copy(
                  results =
                    baselineResults.map {
                      it.copy(baselineState = null, suppressions = suppressions)
                    }
                )
              }
          )
        // Mark new results as new and not suppressed
        val newSchema =
          copy(
            runs =
              runs.map { run ->
                run.copy(
                  results =
                    results.map {
                      it.copy(baselineState = BaselineState.New, suppressions = emptyList())
                    }
                )
              }
          )
        // Merge the two
        listOf(suppressedBaselineSchema, newSchema)
          .merge(removeUriPrefixes = removeUriPrefixes, log = ::echo)
      }
      Mode.UPDATE -> {
        val baselineResultsByHash = baselineResults.associateBy { it.identityHash }
        val resultsByHash = results.associateBy { it.identityHash }
        // New -> No match in the baseline
        // Unchanged -> Exact match in the baseline.
        // Updated -> Partial match is found. Not sure if we could realistically detect this well
        //            based on just ID and location though. May be that the only change we could
        //            match here would be if the severity changes
        // Absent -> Nothing to report, means this issue was fixed presumably. Not sure how this
        //           would show up in a baseline state tbh
        val baselinedResults =
          results.map { result ->
            val baselineResult = baselineResultsByHash[result.identityHash]
            when {
              baselineResult == null -> {
                // No baseline result, so it's new!
                result.copy(baselineState = BaselineState.New)
              }
              baselineResult.shallowHash == result.shallowHash -> {
                // They're they same, so it's unchanged
                result.copy(baselineState = BaselineState.Unchanged, suppressions = suppressions)
              }
              else -> {
                // They're different, so it's updated
                result.copy(baselineState = BaselineState.Updated, suppressions = suppressions)
              }
            }
          }
        val absentResults =
          if (includeAbsent) {
            // Create a copy of the baseline results that are absent with a suppression
            baselineResults
              .filter { result -> result.identityHash !in resultsByHash }
              .map { it.copy(baselineState = BaselineState.Absent, suppressions = suppressions) }
          } else {
            emptyList()
          }
        val absentResultsSchema =
          baseline.copy(runs = runs.map { run -> run.copy(results = absentResults) })
        val newCurrentSchema = copy(runs = runs.map { run -> run.copy(results = baselinedResults) })

        newCurrentSchema.mergeWith(
          absentResultsSchema,
          removeUriPrefixes = removeUriPrefixes,
          log = ::echo,
        )
      }
    }
  }

  internal enum class Mode {
    /**
     * Merge two SARIFs, this does the following:
     * - Marks the baseline results as "suppressed".
     * - Marks the new results as "new".
     *
     * The two SARIFs are deemed to be distinct results and have no overlaps.
     */
    MERGE,
    /**
     * Update the input SARIF based on a previous baseline:
     * - Marks the new results as "new".
     * - Marks the absent results as "absent" (aka "fixed").
     * - Mark remaining as updated or unchanged.
     * - No changes are made to suppressions.
     */
    UPDATE,
  }
}
