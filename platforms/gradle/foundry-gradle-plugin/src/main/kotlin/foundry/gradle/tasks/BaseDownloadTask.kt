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
package foundry.gradle.tasks

import foundry.common.versioning.VersionNumber
import java.io.IOException
import javax.inject.Inject
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.work.DisableCachingByDefault

/**
 * Downloads a binary from its GitHub releases.
 *
 * Usage:
 * ```
 *     ./gradlew <updateThing>
 * ```
 */
@DisableCachingByDefault
internal abstract class BaseDownloadTask(
  private val targetName: String,
  private val addExecPrefix: Boolean = false,
  private val urlTemplate: (version: String) -> String,
) : DefaultTask(), BootstrapTask {
  @get:Inject abstract val progressLoggerFactory: ProgressLoggerFactory

  @get:Input abstract val version: Property<String>

  @get:OutputFile abstract val outputFile: RegularFileProperty

  @Suppress("NestedBlockDepth")
  @TaskAction
  fun download() {
    val version = version.get()
    val progressLogger = progressLoggerFactory.newOperation(javaClass)

    @Suppress("ReplaceCallWithBinaryOperator") // Groovy interop falls over here
    check(!VersionNumber.parse(version).equals(VersionNumber.UNKNOWN)) {
      "Not a valid $targetName version number! $version"
    }

    val url = urlTemplate(version)
    logger.debug("Downloading $url")
    val request = Request.Builder().url(url).build()
    val fileName = url.substringAfterLast('/')
    progressLogger.start("Download $fileName", "Starting download")

    val progressListener = ProgressLoggerProgressListener(fileName, progressLogger)

    val client =
      OkHttpClient.Builder().addInterceptor(ProgressReportingInterceptor(progressListener)).build()

    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) throw IOException("Unexpected code $response")

      val destinationFile =
        outputFile.asFile.get().apply {
          if (!exists()) {
            createNewFile()
          } else {
            delete()
          }
        }

      progressLogger.progress("Writing to $destinationFile")
      response.body.source().use { source ->
        destinationFile.sink().buffer().use { sink ->
          if (addExecPrefix) {
            sink.writeUtf8(EXEC_PREFIX)
          }
          sink.writeAll(source)
        }
      }

      progressLogger.progress("Setting permissions")
      destinationFile.setExecutable(true)
      progressLogger.completed("Finished!", false)
    }
  }
}

/**
 * An executable prefix to locate JAVA_HOME and a java executable to run with. Inspired by Gradle's
 * wrapper
 */
// language=bash
private val EXEC_PREFIX =
  """#!/bin/sh

    if [ -n "${'$'}JAVA_HOME" ] ; then
        if [ -x "${'$'}JAVA_HOME/jre/sh/java" ] ; then
            # IBM's JDK on AIX uses strange locations for the executables
            JAVACMD=${'$'}JAVA_HOME/jre/sh/java
        else
            JAVACMD=${'$'}JAVA_HOME/bin/java
        fi
        if [ ! -x "${'$'}JAVACMD" ] ; then
            die "ERROR: JAVA_HOME is set to an invalid directory: ${'$'}JAVA_HOME

    Please set the JAVA_HOME variable in your environment to match the
    location of your Java installation."
        fi
    else
        JAVACMD=java
        if ! command -v java >/dev/null 2>&1
        then
            die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

    Please set the JAVA_HOME variable in your environment to match the
    location of your Java installation."
        fi
    fi

     exec "${'$'}JAVACMD" \
       -Xmx512m \
       --add-opens java.base/java.lang=ALL-UNNAMED \
       --add-opens java.base/java.util=ALL-UNNAMED \
       --add-opens jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED \
       --add-opens jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED \
       --add-opens jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
       --add-opens jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED \
       --add-opens jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED \
       --add-opens jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED \
       --add-opens jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED \
       --add-opens jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED \
       --add-opens jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED \
       --add-opens jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED \
       -jar $0 "$@"


  """
    .trimIndent()
