/*
 * Copyright (C) 2024 Slack Technologies, LLC
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
package com.slack.skippy

import com.github.ajalt.clikt.core.CliktCommand
import com.slack.sgp.common.SgpLogger

internal fun SgpLogger.Companion.clikt(command: CliktCommand): SgpLogger = CliktSgpLogger(command)

private class CliktSgpLogger(private val command: CliktCommand) : SgpLogger {
  override fun debug(message: String) {
    command.echo(message)
  }

  override fun info(message: String) {
    command.echo(message)
  }

  override fun lifecycle(message: String) {
    command.echo(message)
  }

  override fun warn(message: String) {
    command.echo(message)
  }

  override fun warn(message: String, error: Throwable) {
    command.echo(message + "\n" + error.stackTraceToString())
  }

  override fun error(message: String) {
    command.echo(message, err = true)
  }

  override fun error(message: String, error: Throwable) {
    command.echo(message + "\n" + error.stackTraceToString(), err = true)
  }
}
