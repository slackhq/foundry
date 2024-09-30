/*
 * Copyright (C) 2022 Slack Technologies, LLC
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
@file:Suppress(
  "LongParameterList", // These are how many parameters are in Clikt options.
  "SpreadOperator", // Not spreading would change semantics.
  "unused",
)

package slack.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.OptionDelegate
import com.github.ajalt.clikt.parameters.options.OptionWithValues
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path
import java.nio.file.Paths

/** A dry run option for [clikt commands][CliktCommand]. */
public fun CliktCommand.dryRunOption(
  vararg names: String = arrayOf("--dry-run"),
  help: String = "Runs this as a dry run, no modifications.",
): OptionWithValues<Boolean, Boolean, Boolean> =
  option(names = names, help = help).flag(default = false)

/** A project dir option for [clikt commands][CliktCommand]. */
public fun CliktCommand.projectDirOption(
  vararg names: String = arrayOf("--project-dir"),
  help: String = "The project directory. Defaults to the current working directory.",
): OptionDelegate<Path> =
  option(names = names, help = help).path(mustExist = true, canBeFile = false).defaultLazy {
    Paths.get("").toAbsolutePath()
  }
