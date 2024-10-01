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

import kotlin.time.Duration.Companion.minutes

private const val OOM_GROUPING_HASH = "oom"

/** A set of known issues. */
@Suppress("unused") // We look these up reflectively at runtime
internal object KnownIssues {
  // A simple fake checker for testing this script
  val fakeFailure =
    Issue(
      message = "Fake failure",
      logMessage = "Detected fake failure. Beep boop.",
      matchingText = listOf("FAKE FAILURE NOT REAL"),
      matchingPatterns = listOf(".*FAKE_FAILURE_[a-zA-Z].*".toRegex()),
      groupingHash = "fake-failure",
      retrySignal = RetrySignal.Ack,
    )

  val ftlRateLimit =
    Issue(
      message = "FTL rate limit",
      matchingText = listOf("429 Too Many Requests"),
      logMessage = "Detected FTL rate limit. Retrying in 1 minute.",
      groupingHash = "ftl-rate-limit",
      retrySignal = RetrySignal.RetryDelayed(1.minutes),
    )

  val oom =
    Issue(
      message = "Generic OOM",
      matchingText = listOf("Java heap space"),
      logMessage = "Detected OOM. Retrying immediately.",
      groupingHash = OOM_GROUPING_HASH,
      retrySignal = RetrySignal.RetryImmediately,
    )

  val ftlInfrastructureFailure =
    Issue(
      message = "Inconclusive FTL infrastructure failure",
      matchingText = listOf("Infrastructure failure"),
      logMessage = "Detected inconclusive FTL infrastructure failure. Retrying immediately.",
      groupingHash = "ftl-infrastructure-failure",
      retrySignal = RetrySignal.RetryImmediately,
    )

  val flankTimeout =
    Issue(
      message = "Flank timeout",
      groupingHash = "flank-timeout",
      matchingText = listOf("Canceling flank due to timeout"),
      logMessage = "Detected a flank timeout. Retrying immediately.",
      retrySignal = RetrySignal.RetryImmediately,
    )

  val r8Oom =
    Issue(
      message = "R8 OOM",
      matchingText = listOf("Out of space in CodeCache"),
      logMessage = "Detected a OOM in R8. Retrying immediately.",
      groupingHash = OOM_GROUPING_HASH,
      retrySignal = RetrySignal.RetryImmediately,
    )

  val oomKilledByKernel =
    Issue(
      message = "OOM killed by kernel",
      groupingHash = OOM_GROUPING_HASH,
      matchingText = listOf("Gradle build daemon disappeared unexpectedly"),
      logMessage = "Detected a OOM that was killed by the kernel. Retrying immediately.",
      retrySignal = RetrySignal.RetryImmediately,
    )

  val bugsnagUploadFailed =
    Issue(
      message = "Bugsnag artifact upload failure",
      groupingHash = "bugsnag-upload-failure",
      matchingText = listOf("Bugsnag request failed to complete"),
      logMessage = "Detected bugsnag failed to upload. Retrying immediately.",
      retrySignal = RetrySignal.RetryImmediately,
    )
}
