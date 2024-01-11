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
