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
package slack.stats

import com.squareup.moshi.JsonClass
import com.squareup.moshi.adapter
import java.io.File
import okio.buffer
import okio.sink
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import slack.gradle.util.JsonTools
import slack.stats.LocTask.LocData

/**
 * A simple task that counts Lines of Code (LoC) for Java, Kotlin, and XML files in a given set of
 * directories.
 *
 * This outputs data into a JSON file at [outputFile] in the [LocData] structure.
 */
@CacheableTask
internal abstract class LocTask : DefaultTask() {
  @get:SkipWhenEmpty
  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:InputDirectory
  abstract val srcsDir: DirectoryProperty

  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:InputDirectory
  @get:Optional
  abstract val generatedSrcsDir: DirectoryProperty

  @get:Input @get:Optional abstract val logVerbosely: Property<Boolean>

  @get:OutputFile abstract val outputFile: RegularFileProperty

  @TaskAction
  fun count() {
    val shouldLog = logVerbosely.getOrElse(false)
    val taskLogger = logger
    val processLogger: (String) -> Unit = { log ->
      if (shouldLog) {
        taskLogger.debug(log)
      }
    }

    val srcs = processDir(srcsDir.asFile.get(), processLogger)
    val generatedSrcs =
      if (generatedSrcsDir.isPresent) {
        processDir(generatedSrcsDir.asFile.get(), processLogger)
      } else {
        emptyMap()
      }
    outputFile.asFile.get().sink().buffer().use { sink ->
      JsonTools.MOSHI.adapter<LocData>().toJson(sink, LocData(srcs, generatedSrcs))
    }
  }

  private fun processDir(dir: File, logger: (String) -> Unit): Map<String, LanguageStats> {
    return dir
      .walkTopDown()
      .filter { it.extension in EXTENSION_TO_PROCESSOR }
      .fold(emptyMap()) { stats, file ->
        val languageName = EXTENSION_TO_LANGUAGE.getValue(file.extension)
        val newValues =
          mapOf(languageName to EXTENSION_TO_PROCESSOR.getValue(file.extension)(file, logger))
        stats.mergeWith(newValues)
      }
  }

  companion object {
    internal val EXTENSION_TO_PROCESSOR =
      mapOf("kt" to ::processJvmFile, "java" to ::processJvmFile, "xml" to ::processXmlFile)

    internal val EXTENSION_TO_LANGUAGE = mapOf("kt" to "Kotlin", "java" to "Java", "xml" to "XML")

    internal fun processJvmFile(file: File, logger: (String) -> Unit = {}): LanguageStats {
      var blank = 0
      var comment = 0
      var code = 0
      logger("Logging LoC of $file")
      file.useLines { lines ->
        var isInMultiLineComment = false
        for (line in lines) {
          if (!isInMultiLineComment && line.isBlank()) {
            logger("blnk|$line")
            blank++
            continue
          }
          val trimmedStart = line.trimStart()
          if (!isInMultiLineComment && trimmedStart.startsWith("//")) {
            logger("comt|$line")
            comment++
            continue
          }
          if (isInMultiLineComment) {
            logger("mcmt|$line")
            comment++
            if (trimmedStart.startsWith("*/")) {
              // Exiting a multiline comment
              isInMultiLineComment = false
            }
            continue
          } else if (trimmedStart.startsWith("/*")) {
            // Check if it's closed in this line
            if (trimmedStart.trimEnd().endsWith("*/")) {
              // Closed
              comment++
              continue
            }
            val rest = trimmedStart.substringAfter("*/")
            logger("comt|$line")
            if (rest == trimmedStart) {
              // Not found, it's multi-line
              comment++
              isInMultiLineComment = true
              continue
            }

            // It was found but there was more code after
          }
          logger("code|$line")
          code++
        }
      }
      return LanguageStats(
        // This is always one per file and aggregated later
        files = 1,
        code = code,
        comment = comment,
        blank = blank
      )
    }

    internal fun processXmlFile(file: File, logger: (String) -> Unit = {}): LanguageStats {
      var blank = 0
      var comment = 0
      var code = 0
      file.useLines { lines ->
        var isInMultiLineComment = false
        for (line in lines) {
          if (!isInMultiLineComment && line.isBlank()) {
            logger("blnk|$line")
            blank++
            continue
          }
          val trimmedStart = line.trimStart()
          if (isInMultiLineComment) {
            logger("mcmt|$line")
            comment++
            if (trimmedStart.startsWith("-->")) {
              // Exiting a multiline comment
              isInMultiLineComment = false
            }
            continue
          } else if (trimmedStart.startsWith("<!--")) {
            // Check if it's closed in this line
            if (trimmedStart.trimEnd().endsWith("-->")) {
              // Closed
              comment++
              continue
            }
            val rest = trimmedStart.substringAfter("-->")
            logger("comt|$line")
            if (rest == trimmedStart) {
              // Not found, it's multi-line
              comment++
              isInMultiLineComment = true
              continue
            }

            // It was found but there was more code after
          }
          logger("code|$line")
          code++
        }
      }
      return LanguageStats(
        // This is always one per file and aggregated later
        files = 1,
        code = code,
        comment = comment,
        blank = blank
      )
    }
  }

  @JsonClass(generateAdapter = true)
  data class LocData(
    val srcs: Map<String, LanguageStats>,
    val generatedSrcs: Map<String, LanguageStats>
  ) {
    companion object {
      val EMPTY = LocData(emptyMap(), emptyMap())
      val EMPTY_JSON by lazy { JsonTools.MOSHI.adapter<LocData>().toJson(EMPTY) }
    }
  }
}
