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
package foundry.cli.shellsentry

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * An issue that can be reported to Bugsnag.
 *
 * @property message the message shown in the bugsnag report message. Should be human-readable.
 * @property logMessage the message shown in the CI log when [matchingText] is found. Should be
 *   human-readable.
 * @property groupingHash grouping hash for reporting to bugsnag. This should usually be unique, but
 *   can also be reused across issues that are part of the same general issue.
 * @property description an optional description of the issue. Not used in the CLI, just there for
 *   documentation in the config.
 * @property matchingText a list of matching texts to look for in the log.
 * @property matchingPatterns a list of matching regexp patterns to look for in the log.
 * @property retrySignal the [RetrySignal] to use when this issue is found.
 */
@JsonClass(generateAdapter = true)
public data class Issue
@JvmOverloads
constructor(
  val message: String,
  @Json(name = "log_message") val logMessage: String,
  @Json(name = "grouping_hash") val groupingHash: String,
  @Json(name = "retry_signal") val retrySignal: RetrySignal,
  val description: String? = null,
  @Json(name = "matching_text") val matchingText: List<String> = emptyList(),
  @Json(name = "matching_patterns") val matchingPatterns: List<Regex> = emptyList(),
) {

  init {
    check(matchingText.isNotEmpty() || matchingPatterns.isNotEmpty()) {
      "Issue must have at least one matching text or pattern."
    }
  }

  private inline fun List<String>.checkMatches(check: (line: String) -> Boolean): Boolean {
    return any { check(it) }
  }

  /** Checks the log for this issue and returns a [RetrySignal] if it should be retried. */
  @Suppress("ReturnCount")
  internal fun check(lines: List<String>, log: (String) -> Unit): RetrySignal {
    if (matchingText.isNotEmpty()) {
      for (matchingText in matchingText) {
        if (lines.checkMatches { it.contains(matchingText, ignoreCase = true) }) {
          log(logMessage)
          return retrySignal
        }
      }
    }

    if (matchingPatterns.isNotEmpty()) {
      for (pattern in matchingPatterns) {
        if (lines.checkMatches { pattern.matches(it) }) {
          log(logMessage)
          return retrySignal
        }
      }
    }

    return RetrySignal.Unknown
  }
}

/**
 * Base class for an issue that can be reported to Bugsnag. This is a [Throwable] for BugSnag
 * purposes but doesn't fill in a stacktrace.
 */
public abstract class NoStacktraceThrowable(message: String) : Throwable(message) {
  override fun fillInStackTrace(): Throwable {
    // Do nothing, the stacktrace isn't relevant for these!
    return this
  }
}

/** Common [Throwable] for all [Issue]s. This is used for reporting to Bugsnag. */
internal class KnownIssue(issue: Issue) : NoStacktraceThrowable(issue.message)
