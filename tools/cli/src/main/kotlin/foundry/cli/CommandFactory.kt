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
package foundry.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import java.util.ServiceLoader
import kotlin.system.exitProcess

/** Marker interface for a [CliktCommand] that can be aggregated and loaded via service loader. */
public interface CommandFactory {
  public val key: String
  public val description: String

  public fun create(): CliktCommand
}

/**
 * Primary entry point to run any command registered via [CommandFactory]. First argument should be
 * the command key and remaining arguments are passed to the created CLI.
 */
public fun runCommand(args: List<String>, exitOnError: Boolean = true) {
  val commands = ServiceLoader.load(foundry.cli.CommandFactory::class.java).associateBy { it.key }

  if (args.isEmpty()) {
    System.err.println("Usage: <command> <args>")
    System.err.println("Available commands:")
    commands.forEach { (key, factory) -> System.err.println("  $key: ${factory.description}") }
    if (exitOnError) {
      exitProcess(1)
    }
  }

  val command = args[0]
  val commandArgs =
    when (args.size) {
      1 -> emptyList()
      else -> args.subList(1, args.size)
    }

  commands[command]?.create()?.main(commandArgs) ?: error("Unknown command: '$command'")
}
