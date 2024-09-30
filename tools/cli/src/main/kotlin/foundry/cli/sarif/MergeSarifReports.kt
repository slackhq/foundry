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
package foundry.cli.sarif

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import com.google.auto.service.AutoService
import foundry.cli.CommandFactory
import io.github.detekt.sarif4k.SarifSchema210
import io.github.detekt.sarif4k.SarifSerializer
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolute
import kotlin.io.path.absolutePathString
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.system.exitProcess
import slack.cli.projectDirOption
import slack.cli.skipBuildAndCacheDirs
import slack.cli.walkEachFile

@Suppress("TooManyFunctions")
public class MergeSarifReports : CliktCommand() {

  @AutoService(foundry.cli.CommandFactory::class)
  public class Factory : foundry.cli.CommandFactory {
    override val key: String = "merge-sarif-reports"
    override val description: String = DESCRIPTION

    override fun create(): CliktCommand = MergeSarifReports()
  }

  override fun help(context: Context): String = DESCRIPTION

  private val projectDir by projectDirOption()
  private val outputFile by option("--output-file").path().required()
  private val filePrefix by option("--file-prefix")
  private val argFiles by
    argument("--files").path(mustExist = true, canBeDir = false, mustBeReadable = true).multiple()
  private val verbose by option("--verbose", "-v").flag()
  private val remapSrcRoots by
    option(
        "--remap-src-roots",
        help =
          "When enabled, remaps uri roots to include the subproject path (relative to the root project).",
      )
      .flag()

  private val removeUriPrefixes by
    option(
        "--remove-uri-prefixes",
        help =
          "When enabled, removes the root project directory from location uris such that they are only " +
            "relative to the root project dir.",
      )
      .flag()

  private val allowEmpty by
    option(
        "--allow-empty",
        help = "Flag to allow graceful exiting if no sarif files are found.",
        envvar = "SARIF_MERGING_ALLOW_EMPTY",
      )
      .flag()

  private val level by levelOption()

  private fun log(message: String) {
    if (verbose) {
      echo(message)
    }
  }

  private fun prepareOutput() {
    outputFile.deleteIfExists()
    outputFile.createParentDirectories()
  }

  @OptIn(ExperimentalPathApi::class)
  private fun findBuildFiles(): List<Path> {
    log("Finding build files in ${projectDir.toFile().canonicalFile}")
    val buildFiles =
      projectDir
        .absolute()
        .walkEachFile { skipBuildAndCacheDirs() }
        .filter { it.name == "build.gradle.kts" }
        .toList()
    log("${buildFiles.size} build files found")
    return buildFiles
  }

  private fun String.prefixPathWith(prefix: String) = "$prefix/$this"

  @OptIn(ExperimentalPathApi::class)
  private fun findSarifFiles(): List<Path> {
    require(filePrefix != null || argFiles.isNotEmpty()) {
      "Must specify either --file-prefix or pass files as arguments"
    }

    val files = mutableListOf<Path>()

    files += argFiles

    filePrefix?.let { prefix ->
      // Find build files first, this gives us an easy hook to then go looking in build/reports
      // dirs. Otherwise we don't have a way to easily exclude populated build dirs that would
      // take forever.
      val buildFiles = findBuildFiles()

      log("Finding sarif files")
      files +=
        buildFiles.asSequence().flatMap { buildFile ->
          val reportsDir = buildFile.parent.resolve("build/reports")
          if (reportsDir.exists()) {
            reportsDir.walk().filter {
              it.isRegularFile() &&
                it.extension == "sarif" &&
                it.nameWithoutExtension.startsWith(prefix)
            }
          } else {
            emptySequence()
          }
        }
    }

    return files
  }

  /**
   * Remaps the srcRoots in the sarif file to be relative to the _root_ project root.
   *
   * This is necessary because, when running lint on a standalone module, the source roots are
   * relative to that _module_'s directory. However, we want the merged sarif to be relative to the
   * root project directory.
   *
   * This function performs that in two steps:
   * 1. Remap the `Run.originalUriBaseIds.%SRCROOT%` to be the root project dir ([projectDir]).
   * 2. Remap the `Result.locations.physicalLocation.artifactLocation.uri` to be relative to the
   *    root project dir by prefixing the path with the module name.
   *
   * ## Example
   *
   * If we have a project with the following structure:
   * ```
   * apps/app-core/src/main/java/com/example/app/MainActivity.kt
   * libraries/lib/src/main/java/com/example/app/LibActivity.kt
   * ```
   *
   * The standard sarif report would denote paths like this:
   * ```
   * src/main/java/com/example/app/MainActivity.kt
   * src/main/java/com/example/app/LibActivity.kt
   * ```
   *
   * And what we actually want is this:
   * ```
   * apps/app-core/src/main/java/com/example/app/MainActivity.kt
   * libraries/lib/src/main/java/com/example/app/LibActivity.kt
   * ```
   */
  private fun SarifSchema210.remapSrcRoots(sarifFile: Path): SarifSchema210 {
    //   <module>/─────────────────────────┐
    //       build/─────────────────┐      │
    //          reports/──────┐     │      │
    //                        ▼     ▼      ▼
    val module = sarifFile.parent.parent.parent
    check(module.resolve("build.gradle.kts").exists()) {
      "Expected to find build.gradle.kts in $module"
    }
    val modulePrefix = module.relativeTo(projectDir).toString()
    return copy(
      runs =
        runs.map { run ->
          val originalUri = run.originalURIBaseIDS!!.getValue(SRC_ROOT)
          run.copy(
            // Remap "%SRCROOT%" to be the root project dir.
            originalURIBaseIDS =
              mapOf(
                SRC_ROOT to
                  originalUri.copy(
                    uri =
                      "file://${projectDir
                  .toFile().canonicalPath}/"
                  )
              ),
            // Remap results to add the module prefix to the artifactLocation.uri.
            results =
              run.results?.map { result ->
                result.copy(
                  locations =
                    result.locations?.map { location ->
                      location.copy(
                        physicalLocation =
                          location.physicalLocation?.copy(
                            artifactLocation =
                              location.physicalLocation
                                ?.artifactLocation
                                ?.copy(
                                  uri =
                                    location.physicalLocation
                                      ?.artifactLocation
                                      ?.uri
                                      ?.prefixPathWith(modulePrefix)
                                )
                          )
                      )
                    }
                )
              },
          )
        }
    )
  }

  /** Removes the [projectDir] prefix from location URIs */
  private fun SarifSchema210.remapUris(): SarifSchema210 {
    return copy(
      runs =
        runs.map { run ->
          run.copy(
            results =
              run.results?.map { result ->
                result.copy(
                  locations =
                    result.locations?.map { location ->
                      location.copy(
                        physicalLocation =
                          location.physicalLocation?.let { physicalLocation ->
                            physicalLocation.copy(
                              artifactLocation =
                                physicalLocation.artifactLocation?.let { artifactLocation ->
                                  artifactLocation.copy(
                                    uri =
                                      artifactLocation.uri
                                        ?.removePrefix("file://")
                                        ?.removePrefix(projectDir.absolutePathString())
                                        ?.removePrefix("/")
                                  )
                                }
                            )
                          }
                      )
                    }
                )
              }
          )
        }
    )
  }

  private fun loadSarifs(inputs: List<Path>): List<SarifSchema210> {
    return inputs.map { sarifFile ->
      log("Parsing $sarifFile")
      val parsed = SarifSerializer.fromJson(sarifFile.readText())
      if (parsed.runs.isEmpty() || parsed.runs[0].results.orEmpty().isEmpty()) {
        return@map parsed
      }
      if (remapSrcRoots) {
        parsed.remapSrcRoots(sarifFile)
      } else if (removeUriPrefixes) {
        parsed.remapUris()
      } else {
        parsed
      }
    }
  }

  private fun merge(inputs: List<Path>) {
    log("Parsing ${inputs.size} sarif files")
    val parsed = loadSarifs(inputs)
    val mergedSarif =
      when (parsed.size) {
        0 -> error("No sarif files parsed. Consider using --allow-empty")
        1 -> parsed[0]
        else ->
          parsed.merge(levelOverride = level, removeUriPrefixes = removeUriPrefixes, log = ::log)
      }
    log("Writing merged sarif to $outputFile")
    prepareOutput()
    outputFile.writeText(SarifSerializer.toJson(mergedSarif))
  }

  override fun run() {
    val sarifFiles = findSarifFiles()
    if (sarifFiles.isEmpty()) {
      if (allowEmpty) {
        println("No sarif files found, skipping merging")
        exitProcess(0)
      } else {
        System.err.println("No sarif files found! Did you run lint/detekt first?")
        exitProcess(1)
      }
    }
    merge(sarifFiles)
  }

  private companion object {
    private const val SRC_ROOT = "%SRCROOT%"
    const val DESCRIPTION =
      "Merges all matching sarif reports into a single file for ease of uploading."
  }
}
