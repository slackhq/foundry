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
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import slack.gradle.util.sneakyNull

internal fun String.executeBlocking(providers: ProviderFactory, workingDir: File) {
  executeBlockingWithResult(providers, workingDir)
}

internal fun String.executeBlockingWithResult(
  providers: ProviderFactory,
  workingDir: File
): String? = split(" ").executeBlockingWithResult(providers, workingDir)

internal fun List<String>.executeBlockingWithResult(
  providers: ProviderFactory,
  workingDir: File
): String? = executeBlockingWithResult(providers, workingDir, this)

internal fun executeBlockingWithResult(
  providers: ProviderFactory,
  workingDir: File? = null,
  arguments: List<String>
): String? {
  return executeWithResult(providers, workingDir, arguments).orNull
}

internal fun executeWithResult(
  providers: ProviderFactory,
  inputWorkingDir: File? = null,
  arguments: List<String>
): Provider<String> {
  return providers
    .exec {
      // Apparently Gradle wants us to distinguish between the executable and its arguments, so...
      // we try to futz that here. But also this is silly.
      commandLine(arguments[0])
      args = arguments.drop(1)
      inputWorkingDir?.let { workingDir(it) }
    }
    .standardOutput
    .asText
    .map { it.trimAtEnd() }
    .map { it.ifBlank { sneakyNull() } }
}

private fun String.trimAtEnd(): String {
  return ("x$this").trim().substring(1)
}
