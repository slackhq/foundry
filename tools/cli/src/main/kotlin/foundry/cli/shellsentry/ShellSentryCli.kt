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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import com.google.auto.service.AutoService
import foundry.cli.CommandFactory
import org.jetbrains.annotations.TestOnly
import slack.cli.projectDirOption

/**
 * Executes a command with Bugsnag tracing and retries as needed. This CLI is a shim over
 * [ShellSentry].
 *
 * Example:
 * ```
 * $ ./<binary> --bugsnag-key=1234 --verbose --configurationFile config.json ./gradlew build
 * ```
 */
public class ShellSentryCli : CliktCommand(DESCRIPTION) {

  private companion object {
    const val DESCRIPTION = "Executes a command with Bugsnag tracing and retries as needed."
  }

  @AutoService(foundry.cli.CommandFactory::class)
  public class Factory : foundry.cli.CommandFactory {
    override val key: String = "shell-sentry"
    override val description: String = DESCRIPTION

    override fun create(): CliktCommand = ShellSentryCli()
  }

  internal val projectDir by projectDirOption()

  internal val verbose by option("--verbose", "-v").flag()

  internal val bugsnagKey by option("--bugsnag-key", envvar = "PE_BUGSNAG_KEY")

  internal val configurationFile by
    option("--config", envvar = "PE_CONFIGURATION_FILE")
      .path(mustExist = true, canBeFile = true, canBeDir = false)

  internal val debug by option("--debug", "-d").flag()

  @get:TestOnly
  internal val noExit by
    option(
        "--no-exit",
        help = "Instructs this CLI to not exit the process with the status code. Test only!",
      )
      .flag()

  @get:TestOnly internal val parseOnly by option("--parse-only").flag(default = false)

  internal val args by argument().multiple()

  override fun run() {
    if (parseOnly) return

    return ShellSentry.create(this).exec()
  }
}
