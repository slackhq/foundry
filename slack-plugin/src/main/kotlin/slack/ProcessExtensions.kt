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
import okio.Buffer
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.process.ExecOperations

internal fun String.executeBlocking(execOps: ExecOperations, workingDir: File) {
  execOps
    .exec {
      commandLine(this@executeBlocking)
      workingDir(workingDir)
    }
    .rethrowFailure()
}

internal fun String.executeBlockingWithResult(execOps: ExecOperations, workingDir: File): String? =
  split(" ").executeBlockingWithResult(execOps, workingDir)

internal fun List<String>.executeBlockingWithResult(
  execOps: ExecOperations,
  workingDir: File
): String? = executeBlockingWithResult(execOps, workingDir, *toTypedArray())

internal fun executeBlockingWithResult(
  execOps: ExecOperations,
  workingDir: File? = null,
  vararg args: String
): String? {
  return try {
    val stdOut = Buffer()
    stdOut.outputStream().use { os ->
      execOps
        .exec {
          args(*args)
          workingDir?.let { workingDir(it) }
          this.standardOutput = os
        }
        .rethrowFailure()
    }
    stdOut.readUtf8().trimAtEnd()
  } catch (ignored: Throwable) {
    null
  }
}

internal fun executeWithResult(
  providers: ProviderFactory,
  workingDir: File? = null,
  vararg args: String
): Provider<String> {
  return providers
    .exec {
      args(*args)
      workingDir?.let { workingDir(it) }
    }
    .standardOutput
    .asText
}

private fun String.trimAtEnd(): String {
  return ("x$this").trim().substring(1)
}
