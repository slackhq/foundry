/*
 * Copyright (C) 2013 Slack Technologies, LLC
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
@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package com.android.build.gradle.internal.tasks

import com.android.utils.FileUtils
import com.google.common.base.Charsets
import com.google.common.io.FileWriteMode
import com.google.common.io.Files
import java.io.File
import java.io.IOException
import java.lang.Boolean as JavaBoolean
import java.util.Locale
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.work.DisableCachingByDefault

/**
 * Task to merge files. This appends all the files together into an output file.
 *
 * Caching disabled by default for this task because the task does very little work. Concatenates
 * the registered Inputs into a single Output file, requiring no computation. Calculating cache
 * hit/miss and fetching results is likely more expensive than simply executing the task.
 *
 * MONKEY PATCH NOTES: This task is monkey patched to sort files before merging in order to make
 * this task deterministic. This is controlled by the `com.slack.sgp.sort-merge-files` system
 * property. Also removed `@BuildAnalyzer` from the task because its API changed in later AGP
 * versions.
 */
@DisableCachingByDefault
public abstract class MergeFileTask : NonIncrementalTask() {

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  public abstract val inputFiles: ConfigurableFileCollection

  @get:OutputFile public abstract val outputFile: RegularFileProperty

  @Throws(IOException::class)
  override fun doTaskAction() {
    mergeFiles(inputFiles.files, outputFile.get().asFile)
  }

  public companion object {

    public fun mergeFiles(inputFiles: Collection<File>, output: File) {
      // filter out any non-existent files
      val existingFiles = inputFiles.filter { it.isFile() }

      val filesToUse =
        if (JavaBoolean.getBoolean("com.slack.sgp.sort-merge-files")) {
          if (JavaBoolean.getBoolean("com.slack.sgp.sort-merge-files.log")) {
            println("Sorting ${existingFiles.size} files in merge task. Files are $existingFiles")
          }
          existingFiles.sortedBy { it.invariantSeparatorsPath.lowercase(Locale.US) }
        } else {
          existingFiles
        }

      if (filesToUse.size == 1) {
        FileUtils.copyFile(filesToUse[0], output)
        return
      }

      // first delete the current file
      FileUtils.deleteIfExists(output)

      // no input? done.
      if (filesToUse.isEmpty()) {
        return
      }

      // otherwise put all the files together
      for (file in filesToUse) {
        val content = Files.asCharSource(file, Charsets.UTF_8).read()
        Files.asCharSink(output, Charsets.UTF_8, FileWriteMode.APPEND).write("$content\n")
      }
    }
  }
}
