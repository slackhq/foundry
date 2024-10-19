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

import com.github.ajalt.clikt.core.main
import com.squareup.moshi.adapter
import eu.jrie.jetbrains.kotlinshell.shell.shell
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteRecursively
import kotlin.system.exitProcess
import okio.buffer
import okio.source

/**
 * Executes a command with Bugsnag tracing and retries as needed.
 *
 * @property command the command to execute (i.e. './gradlew build').
 * @property workingDir the working directory to execute the command in.
 * @property cacheDir the directory to use for caching temporary files. Defaults to
 *   [createTempDirectory].
 * @property bugsnagKey optional Bugsnag API key to use for reporting.
 * @property config the [ShellSentryConfig] to use.
 * @property verbose whether to print verbose output.
 * @property debug whether to keep the cache directory around for debugging. Otherwise, it will be
 *   deleted at the end.
 * @property noExit whether to exit the process with the exit code. This is useful for testing.
 * @property logger a function to log output to. Defaults to [println].
 */
@Suppress("LongParameterList")
public data class ShellSentry(
  private val command: String,
  private val workingDir: Path,
  private val cacheDir: Path = createTempDirectory("shellsentry"),
  private val bugsnagKey: String? = null,
  private val config: ShellSentryConfig = ShellSentryConfig(),
  private val verbose: Boolean = false,
  private val debug: Boolean = false,
  private val noExit: Boolean = false,
  private val logger: (String) -> Unit = ::println,
  private val extensions: List<ShellSentryExtension> = emptyList(),
) {

  @Suppress("CyclomaticComplexMethod", "LongMethod")
  @OptIn(ExperimentalPathApi::class)
  public fun exec() {
    // Initial command execution
    val (initialExitCode, initialLogFile) = executeCommand(command, cacheDir)
    var exitCode = initialExitCode
    var logFile = initialLogFile
    var attempts = 0
    while (exitCode != 0 && attempts < 1) {
      attempts++
      logger(
        "Command failed with exit code $exitCode. Running processor script (attempt $attempts)..."
      )

      logger("Processing CI failure")
      val resultProcessor = ResultProcessor(verbose, bugsnagKey, config, logger, extensions)

      when (val retrySignal = resultProcessor.process(command, exitCode, logFile, false)) {
        is RetrySignal.Ack,
        RetrySignal.Unknown -> {
          logger("Processor exited with 0, exiting with original exit code...")
          break
        }
        is RetrySignal.RetryDelayed -> {
          logger(
            "Processor script exited with 2, rerunning the command after ${retrySignal.delay}..."
          )
          // TODO add option to reclaim memory?
          Thread.sleep(retrySignal.delay.inWholeMilliseconds)
          val secondResult = executeCommand(command, cacheDir)
          exitCode = secondResult.exitCode
          logFile = secondResult.outputFile
          if (secondResult.exitCode != 0) {
            // Process the second failure, then bounce out
            resultProcessor.process(
              command,
              secondResult.exitCode,
              secondResult.outputFile,
              isAfterRetry = true,
            )
          }
        }
        is RetrySignal.RetryImmediately -> {
          logger("Processor script exited with 1, rerunning the command immediately...")
          // TODO add option to reclaim memory?
          val secondResult = executeCommand(command, cacheDir)
          exitCode = secondResult.exitCode
          logFile = secondResult.outputFile
          if (secondResult.exitCode != 0) {
            // Process the second failure, then bounce out
            resultProcessor.process(
              command,
              secondResult.exitCode,
              secondResult.outputFile,
              isAfterRetry = true,
            )
          }
        }
      }
    }

    // If we got here, all is well
    // Delete the tmp files
    if (!debug) {
      cacheDir.deleteRecursively()
    }

    logger("Exiting with code $exitCode")
    if (!noExit) {
      exitProcess(exitCode)
    }
  }

  // Function to execute command and capture output. Shorthand to the testable top-level function.
  private fun executeCommand(command: String, tmpDir: Path) =
    executeCommand(workingDir, command, tmpDir, logger)

  public companion object {
    /** Creates a new instance with the given [argv] command line args as input. */
    public fun create(argv: List<String>, echo: (String) -> Unit): ShellSentry {
      val cli = ShellSentryCli().apply { main(listOf("--parse-only") + argv) }
      return create(cli, echo)
    }

    /** Internal function to consolidate CLI args -> [ShellSentry] creation logic. */
    internal fun create(
      cli: ShellSentryCli,
      logger: (String) -> Unit = { cli.echo(it) },
    ): ShellSentry {
      val moshi = ProcessingUtil.newMoshi()
      val config =
        cli.configurationFile?.let {
          logger("Parsing config file '$it'")
          it.source().buffer().use { source -> moshi.adapter<ShellSentryConfig>().fromJson(source) }
        } ?: ShellSentryConfig()

      // Temporary dir for command output
      val cacheDir = cli.projectDir.resolve("tmp/shellsentry")
      cacheDir.createDirectories()

      return ShellSentry(
        command = cli.args.joinToString(" "),
        workingDir = cli.projectDir,
        cacheDir = cacheDir,
        config = config,
        verbose = cli.verbose,
        bugsnagKey = cli.bugsnagKey,
        debug = cli.debug,
        noExit = cli.noExit,
        logger = logger,
      )
    }
  }
}

internal data class ProcessResult(val exitCode: Int, val outputFile: Path)

// Function to execute command and capture output
internal fun executeCommand(
  workingDir: Path,
  command: String,
  tmpDir: Path,
  echo: (String) -> Unit,
): ProcessResult {
  echo("Running command: '$command'")

  val tmpFile = createTempFile(tmpDir, "shellsentry", ".txt").toAbsolutePath()

  var exitCode = 0
  shell {
    // Weird but the only way to set the working dir
    shell(dir = workingDir.toFile()) {
      // Read the output of the process and write to both stdout and file
      // This makes it behave a bit like tee.
      val echoHandler = stringLambda { line ->
        // The line always includes a trailing newline, but we don't need that
        echo(line.removeSuffix("\n"))
        // Pass the line through unmodified
        line to ""
      }
      val process = command.process() forkErr { it pipe echoHandler pipe tmpFile.toFile() }
      pipeline { process pipe echoHandler pipe tmpFile.toFile() }.join()
      exitCode = process.process.pcb.exitCode
    }
  }

  return ProcessResult(exitCode, tmpFile)
}
