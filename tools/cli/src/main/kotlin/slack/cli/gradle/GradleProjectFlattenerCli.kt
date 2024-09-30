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
package slack.cli.gradle

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import com.google.auto.service.AutoService
import java.io.File
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.appendLines
import kotlin.io.path.appendText
import kotlin.io.path.copyToRecursively
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import slack.cli.CommandFactory
import slack.cli.dryRunOption
import slack.cli.projectDirOption

/**
 * A CLI that flattens all gradle projects in a given directory to be top level while preserving
 * their original project paths.
 *
 * This is useful for flattening nested projects that use Dokka, which does not currently support
 * easy doc gen for nested projects and end up with colliding names.
 *
 * It's recommended to run `./gradlew clean` first before running this script to minimize work.
 */
public class GradleProjectFlattenerCli : CliktCommand() {

  private companion object {
    const val DESCRIPTION =
      "A CLI that flattens all gradle projects in a given directory to be top level while " +
        "preserving their original project paths."
  }

  @AutoService(CommandFactory::class)
  public class Factory : CommandFactory {
    override val key: String = "flatten-gradle-projects"
    override val description: String = DESCRIPTION

    override fun create(): CliktCommand = GradleProjectFlattenerCli()
  }

  override fun help(context: Context): String = DESCRIPTION

  private val projectDir by projectDirOption()

  private val settingsFile by
    option(
        "--settings-file",
        "-s",
        help =
          "The settings.gradle file to use. Note this file _must_ only have a single, top-level `include()` call " +
            "with vararg project args.",
      )
      .path(mustExist = true, canBeDir = false)
      .required()

  private val projectDelimiter by option().default("--")

  private val dryRun by dryRunOption()

  private val strict by
    option("--strict", help = "If true, will fail if any of the projects to flatten don't exist.")
      .flag()

  private val verbose by
    option("--verbose", "-v", help = "If true, will print out more information.").flag()

  private val force by
    option("--force", "-f", help = "If true, force overwrite new projects.").flag()

  @ExperimentalPathApi
  override fun run() {
    val projectPaths =
      settingsFile
        .readText()
        .trim()
        .lines()
        // Filter out commented lines
        .filterNot { it.trimStart().startsWith("//") }
        .joinToString("\n")
        .removePrefix("include(")
        .removeSuffix(")")
        .split(",")
        .map { it.trim().removeSurrounding("\"") }

    val newPathMapping = mutableMapOf<String, String>()
    @Suppress("LoopWithTooManyJumpStatements")
    for (path in projectPaths) {
      val realPath =
        projectDir.resolve(path.removePrefix(":").removeSuffix(":").replace(":", File.separator))
      if (strict) {
        check(!path.endsWith(":")) { "Project paths cannot end with ':'" }
        check(realPath.exists()) { "Expected $realPath to exist." }
        check(realPath.isDirectory()) { "Expected $realPath to be a directory." }
      } else if (!realPath.exists()) {
        echo("Skipping $path as it doesn't exist", err = true)
        continue
      }
      val newPath =
        projectDir.resolve(path.removePrefix(":").removeSuffix(":").replace(":", projectDelimiter))
      if (newPath == realPath) {
        // Already top-level, move on
        continue
      }
      newPathMapping[path] = newPath.relativeTo(projectDir).toString()
      if (verbose) {
        echo("Flattening $realPath to $newPath")
      }
      if (!dryRun) {
        realPath.copyToRecursively(newPath, followLinks = false, overwrite = force)
      }
    }

    if (verbose) {
      echo("Finished flattening projects. Updating settings file")
    }
    val newPaths =
      projectPaths.mapNotNull { path ->
        // Point at their new paths
        // Example:
        //   project(":libraries:compose-extensions:pull-refresh").projectDir =
        //     file("libraries--compose-extensions--pull-refresh")
        val newPath = newPathMapping[path] ?: return@mapNotNull null
        "project(\"$path\").projectDir = file(\"$newPath\")"
          .also {
            if (verbose) {
              echo("+  $it")
            }
          }
      }

    if (!dryRun) {
      settingsFile.appendText("\n\n")
      settingsFile.appendLines(newPaths)
    }
  }
}
