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

import com.google.common.truth.Truth.assertThat
import kotlin.io.path.readLines
import kotlin.io.path.readText
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import slack.cli.shellsentry.AnalysisResult
import slack.cli.shellsentry.KnownIssue
import slack.cli.shellsentry.KnownIssues
import slack.cli.shellsentry.ResultProcessor
import slack.cli.shellsentry.RetrySignal
import slack.cli.shellsentry.ShellSentryConfig
import slack.cli.shellsentry.ShellSentryExtension
import slack.cli.shellsentry.executeCommand
import slack.cli.shellsentry.parseBuildScan

@RunWith(Parameterized::class)
class ResultProcessorTest(private val useExtensions: Boolean) {

  companion object {
    @Parameters(name = "useExtensions = {0}")
    @JvmStatic
    fun data(): List<Array<Any>> {
      return listOf(arrayOf(true), arrayOf(false))
    }
  }

  @JvmField @Rule val tmpFolder = TemporaryFolder()

  private val logs = ArrayDeque<String>()

  private val testExtensions =
    listOf(KnownIssues.ftlRateLimit, KnownIssues.oom, KnownIssues.fakeFailure).map { issue ->
      ShellSentryExtension { _, _, _, consoleOutput ->
        val signal = issue.check(consoleOutput.readLines(), logs::add)
        // Give all these 75% confidence. Higher than the default, but not 100 so we can test
        // higher confidence later
        AnalysisResult(issue.message, issue.logMessage, signal, 75) { KnownIssue(issue) }
      }
    }

  @Test
  fun testExecuteCommand() {
    tmpFolder.newFile("test.txt")
    val tmpDir = tmpFolder.newFolder("tmp/shellsentry")
    val (exitCode, outputFile) =
      executeCommand(tmpFolder.root.toPath(), "ls -1", tmpDir.toPath(), logs::add)
    assertThat(exitCode).isEqualTo(0)

    val expectedOutput =
      """
      test.txt
      tmp
    """
        .trimIndent()

    assertThat(outputFile.readText().trim()).isEqualTo(expectedOutput)

    // Note we use "contains" here because our script may output additional logs
    assertThat(logs.joinToString("\n").trim()).contains(expectedOutput)
  }

  @Test
  fun testExecuteCommandWithStderr() {
    val script =
      """
      #!/bin/bash

      >&2 echo "Error text"
    """
        .trimIndent()
    val scriptFile =
      tmpFolder.newFile("script.sh").apply {
        writeText(script)
        setExecutable(true)
      }
    tmpFolder.newFile("test.txt")
    val tmpDir = tmpFolder.newFolder("tmp/shellsentry")
    val (exitCode, outputFile) =
      executeCommand(tmpFolder.root.toPath(), scriptFile.absolutePath, tmpDir.toPath(), logs::add)
    assertThat(exitCode).isEqualTo(0)

    val expectedOutput =
      """
      Error text
    """
        .trimIndent()

    assertThat(outputFile.readText().trim()).isEqualTo(expectedOutput)

    // Note we use "contains" here because our script may output additional logs
    assertThat(logs.joinToString("\n").trim()).contains(expectedOutput)
  }

  @Test
  fun unknownIssue() {
    val outputFile = tmpFolder.newFile("logs.txt")
    outputFile.writeText(
      """
      [1/2] FAILURE: Build failed with an exception.
      """
        .trimIndent()
        .padWithTestLogs()
    )
    val signal = newProcessor().process("", 1, outputFile.toPath(), isAfterRetry = false)
    check(signal is RetrySignal.Unknown)
  }

  @Test
  fun retryDelayed() {
    val outputFile = tmpFolder.newFile("logs.txt")
    outputFile.writeText(
      """
      ${KnownIssues.ftlRateLimit.matchingText}
      """
        .trimIndent()
        .padWithTestLogs()
    )
    val signal = newProcessor().process("", 1, outputFile.toPath(), isAfterRetry = false)
    check(signal is RetrySignal.RetryDelayed)
  }

  @Test
  fun retryImmediately() {
    val outputFile = tmpFolder.newFile("logs.txt")
    outputFile.writeText(
      """
      ${KnownIssues.oom.matchingText}
      """
        .trimIndent()
        .padWithTestLogs()
    )
    val signal = newProcessor().process("", 1, outputFile.toPath(), isAfterRetry = false)
    check(signal is RetrySignal.RetryImmediately)
  }

  @Test
  fun ack() {
    val outputFile = tmpFolder.newFile("logs.txt")
    outputFile.writeText(
      """
      ${KnownIssues.fakeFailure.matchingText}
      """
        .trimIndent()
        .padWithTestLogs()
    )
    val signal = newProcessor().process("", 1, outputFile.toPath(), isAfterRetry = false)
    check(signal is RetrySignal.Ack)
  }

  @Test
  fun matchingPattern_matches() {
    val outputFile = tmpFolder.newFile("logs.txt")
    outputFile.writeText(
      """
      FAKE_FAILURE_a
      """
        .trimIndent()
        .padWithTestLogs()
    )
    val signal = newProcessor().process("", 1, outputFile.toPath(), isAfterRetry = false)
    check(signal is RetrySignal.Ack)
  }

  @Test
  fun matchingPattern_doesNotMatch() {
    val outputFile = tmpFolder.newFile("logs.txt")
    outputFile.writeText(
      """
      FAKE_FAILURE-a
      """
        .trimIndent()
        .padWithTestLogs()
    )
    val signal = newProcessor().process("", 1, outputFile.toPath(), isAfterRetry = false)
    check(signal is RetrySignal.Unknown)
  }

  @Test
  fun parseBuildScan() {
    val url = "https://gradle-enterprise.example.com"
    val scanUrl = "$url/s/ueizlbptdqv6q"
    val log =
      """
      Publishing build scan...
      $scanUrl

    """
        .trimIndent()
        .padWithTestLogs()

    // Assert in both directions they match
    assertThat(log.lines().parseBuildScan(url)).isEqualTo(scanUrl)
    assertThat(log.lines().reversed().parseBuildScan(url)).isEqualTo(scanUrl)
  }

  @Test
  fun lowConfidenceMatch_isSkipped() {
    assumeTrue(useExtensions)
    val outputFile = tmpFolder.newFile("logs.txt")
    outputFile.writeText(
      """
      FAKE_FAILURE_a
      """
        .trimIndent()
        .padWithTestLogs()
    )
    val signal =
      newProcessor(config = ShellSentryConfig(knownIssues = emptyList(), minConfidence = 100))
        .process("", 1, outputFile.toPath(), isAfterRetry = false)
    check(signal is RetrySignal.Unknown)
  }

  private fun newProcessor(
    extensions: List<ShellSentryExtension> = if (useExtensions) testExtensions else emptyList(),
    config: ShellSentryConfig =
      if (!useExtensions) ShellSentryConfig() else ShellSentryConfig(knownIssues = emptyList()),
  ): ResultProcessor {
    return ResultProcessor(
      verbose = true,
      bugsnagKey = null,
      config = config,
      echo = logs::add,
      extensions = extensions,
    )
  }

  // Helper to ensure we're parsing logs from within the test output
  private fun String.padWithTestLogs(): String {
    val prefix = (1..10).joinToString("\n") { randomString() }
    val suffix = (1..10).joinToString("\n") { randomString() }
    return "$prefix\n${randomString()}$this${randomString()}\n$suffix"
  }

  private fun randomString(): String {
    return (0..10).map { ('a'..'z').random() }.joinToString("")
  }
}
