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
package foundry.cli.gradle

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import com.google.auto.service.AutoService
import foundry.cli.CommandFactory
import java.io.File
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolute
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.system.exitProcess
import slack.cli.projectDirOption
import slack.cli.skipBuildAndCacheDirs
import slack.cli.walkEachFile

/** A CLI that verifies a given settings file has only valid projects. */
public class GradleSettingsVerifierCli : CliktCommand() {

  private companion object {
    const val DESCRIPTION = "A CLI that verifies a given settings file has only valid projects."
  }

  @AutoService(foundry.cli.CommandFactory::class)
  public class Factory : foundry.cli.CommandFactory {
    override val key: String = "verify-gradle-settings"
    override val description: String = DESCRIPTION

    override fun create(): CliktCommand = GradleSettingsVerifierCli()
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

  private val implicitPaths by
    option(
        "--implicit-path",
        "-i",
        help =
          "Implicit project names that may not be present in the settings file but should be assumed present.",
      )
      .multiple()

  private val deleteUnIncludedPaths by
    option(
        "--delete-un-included-paths",
        "-d",
        help = "Delete any paths that are not included in the settings file.",
      )
      .flag()

  private fun resolveProjectFromGradlePath(relativePath: String): Path {
    val gradlePath = relativePath.removePrefix(":").removeSuffix(":").replace(":", File.separator)
    return projectDir.resolve(gradlePath)
  }

  @Suppress("LongMethod")
  @ExperimentalPathApi
  override fun run() {
    val implicitPaths = implicitPaths.associateWith { resolveProjectFromGradlePath(it) }
    val projectsViaBuildFiles =
      projectDir
        .absolute()
        .walkEachFile { skipBuildAndCacheDirs() }
        .filter { it.name == "build.gradle.kts" }
        .associateBy { path -> // Get the gradle path relative to the root project dir as the key
          val gradlePath =
            ":" +
              path.parent // project dir
                .relativeTo(projectDir)
                .toString()
                .replace(File.separator, ":")
          gradlePath
        }
        .filterValues { it.parent != projectDir }
        .plus(implicitPaths)

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
        .splitToSequence(",")
        .associateBy { line -> line.trim().removeSuffix(",").removeSurrounding("\"") }
        .plus(implicitPaths.mapValues { "<implicit>" })

    val errors = mutableListOf<String>()
    @Suppress("LoopWithTooManyJumpStatements")
    for ((gradlePath, line) in projectPaths) {
      val realPath = resolveProjectFromGradlePath(gradlePath)

      fun reportError(message: String, column: Int) {
        errors += buildString {
          append(message)
          appendLine(line)
          appendLine("${" ".repeat(column)}^")
        }
      }

      when {
        gradlePath.endsWith(':') -> {
          reportError("Project paths should not end with ':'", line.lastIndexOf(':') - 1)
        }
        !realPath.exists() -> {
          reportError(
            "Project dir '${realPath.relativeTo(projectDir)}' does not exist.",
            line.indexOfFirst { !it.isWhitespace() },
          )
        }
        !realPath.resolve("build.gradle.kts").exists() -> {
          reportError(
            "Project build file '${realPath.relativeTo(projectDir).resolve("build.gradle.kts")}' does not exist.",
            line.indexOfFirst { !it.isWhitespace() },
          )
        }
        !realPath.isDirectory() -> {
          reportError(
            "Expected '$realPath' to be a directory.",
            line.indexOfFirst { !it.isWhitespace() },
          )
        }
      }
    }

    for ((path, buildFile) in projectsViaBuildFiles) {
      if (path !in projectPaths) {
        val projectPath = buildFile.parent
        if (deleteUnIncludedPaths) {
          echo("Deleting un-included project '$path' at $projectPath")
          projectPath.deleteRecursively()
        } else {
          errors += buildString {
            appendLine("Project '$path' is present in the filesystem but not in the settings file.")
            appendLine("Please add it to the settings file or delete it.")
            appendLine("  Project dir:\t${projectPath.relativeTo(projectDir)}")
            appendLine("  Build file:\t${buildFile.relativeTo(projectDir)}")
          }
        }
      }
    }

    if (errors.isNotEmpty()) {
      echo("Errors found in '${settingsFile.name}'. Please fix or remove these.", err = true)
      echo(errors.joinToString(""), err = true)
      exitProcess(1)
    }
  }
}
