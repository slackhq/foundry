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

import java.nio.file.Path

/**
 * [ShellSentryExtension] is an extension API to bring your own, complex checkers to [ShellSentry].
 *
 * Extensions are given the command, exit code, and console output of the failed command. They can
 * then process it however they want and return an [AnalysisResult] if they find an issue.
 *
 * ## Example
 *
 * Below is an example of an extension that asks an AI chat bot to diagnose a failure.
 *
 * ```kotlin
 * public class AiExtension(private val aiClient: AiClient) : ShellSentryExtension {
 *   override fun check(command: String, exitCode: Int, isAfterRetry: Boolean, consoleOutput: Path): AnalysisResult? {
 *     val text = consoleOutput.readText()
 *     val rawAnalysis = aiClient.analyze(text)
 *     return rawAnalysis.toAnalysisResult()
 *   }
 * }
 * ```
 */
public fun interface ShellSentryExtension {
  /**
   * Returns a result of this extension's analysis. Returns null if this extension could not handle
   * failure.
   *
   * @param command the command that was executed.
   * @param exitCode the exit code of the command. Guaranteed to be non-zero.
   * @param isAfterRetry whether this is after a retry.
   * @param consoleOutput the path to the console output of the command.
   * @see AnalysisResult for more details on what goes in a result.
   */
  public fun check(
    command: String,
    exitCode: Int,
    isAfterRetry: Boolean,
    consoleOutput: Path,
  ): AnalysisResult?
}

/** A returned analysis result in a [ShellSentryExtension]. */
public data class AnalysisResult(
  /**
   * A broad single-line description of the error without specifying exact details, suitable for
   * crash reporter grouping.
   */
  val message: String,
  /** A detailed, multi-line message explaining the error and suggesting a solution. */
  val explanation: String,
  /** A [RetrySignal] indicating if this can be retried. */
  val retrySignal: RetrySignal,
  /**
   * A confidence level, on a scale of [0-100]. This is useful for dynamic analysis that made be
   * subject to confidence levels, such as an AI analyzer.
   */
  val confidence: Int,
  /**
   * A function that takes the [message] and returns a [Throwable] for reporting to Bugsnag.
   * Consider subclassing [NoStacktraceThrowable] if needed.
   */
  val throwableMaker: (message: String) -> Throwable,
)
