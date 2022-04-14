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
package slack

import java.io.File
import java.util.concurrent.TimeUnit

internal fun String.execute(workingDir: File) {
  executeBlocking(workingDir)
}

internal fun String.executeBlocking(workingDir: File) {
  Runtime.getRuntime().exec(this, null, workingDir).waitFor()
}

internal fun String.executeWithResult(workingDir: File): String? {
  return executeBlockingWithResult(workingDir)
}

internal fun String.executeBlockingWithResult(workingDir: File): String? {
  return split(" ").executeBlockingWithResult(workingDir)
}

internal fun List<String>.executeBlockingWithResult(workingDir: File): String? {
  return executeBlockingWithResult(workingDir, *toTypedArray())
}

internal fun executeBlockingWithResult(workingDir: File? = null, vararg args: String): String? {
  val process = args.toList().executeProcess(workingDir)
  return try {
    val standardText = process.inputStream.bufferedReader().readText()
    process.errorStream.bufferedReader().readText()

    val finished = process.waitFor(10, TimeUnit.SECONDS)
    if (finished && process.exitValue() == 0) standardText.trimAtEnd() else null
  } finally {
    process.destroyForcibly()
  }
}

internal fun String.executeProcess(workingDir: File? = null): Process {
  return Runtime.getRuntime().exec(this, null, workingDir)
}

internal fun List<String>.executeProcess(workingDir: File? = null): Process {
  return Runtime.getRuntime().exec(toTypedArray(), null, workingDir)
}

private fun String.trimAtEnd(): String {
  return ("x$this").trim().substring(1)
}
