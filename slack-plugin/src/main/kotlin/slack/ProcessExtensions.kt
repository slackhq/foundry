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
import javax.inject.Inject
import okio.Buffer
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.Optional
import org.gradle.process.ExecOperations
import org.gradle.process.ExecSpec
import org.gradle.process.internal.ExecException
import slack.ExecSource.Parameters
import slack.gradle.util.sneakyNull

internal fun String.executeBlocking(
  providers: ProviderFactory,
  workingDir: File,
  isRelevantToConfigurationCache: Boolean
) {
  executeBlockingWithResult(providers, workingDir, isRelevantToConfigurationCache)
}

internal fun String.executeBlockingWithResult(
  providers: ProviderFactory,
  workingDir: File,
  isRelevantToConfigurationCache: Boolean
): String? =
  split(" ").executeBlockingWithResult(providers, workingDir, isRelevantToConfigurationCache)

internal fun List<String>.executeBlockingWithResult(
  providers: ProviderFactory,
  workingDir: File,
  isRelevantToConfigurationCache: Boolean
): String? = executeBlockingWithResult(providers, workingDir, this, isRelevantToConfigurationCache)

internal fun executeBlockingWithResult(
  providers: ProviderFactory,
  workingDir: File? = null,
  arguments: List<String>,
  isRelevantToConfigurationCache: Boolean
): String? {
  return executeWithResult(providers, workingDir, arguments, isRelevantToConfigurationCache).orNull
}

internal fun executeWithResult(
  providers: ProviderFactory,
  inputWorkingDir: File? = null,
  arguments: List<String>,
  isRelevantToConfigurationCache: Boolean
): Provider<String> {
  if (!isRelevantToConfigurationCache) {
    return providers
      .of(ExecSource::class.java) {
        parameters.workingDir.set(inputWorkingDir)
        parameters.args.set(arguments)
      }
      .map { it.value }
  }
  return providers
    .exec { configureExec(inputWorkingDir, arguments) }
    .standardOutput
    .asText
    .map { it.trimAtEnd() }
    .map { it.ifBlank { sneakyNull() } }
}

private fun ExecSpec.configureExec(
  inputWorkingDir: File? = null,
  arguments: List<String>,
) {
  // Apparently Gradle wants us to distinguish between the executable and its arguments, so...
  // we try to futz that here. But also this is silly.
  commandLine(arguments[0])
  args = arguments.drop(1)
  inputWorkingDir?.let { workingDir(it) }
}

private fun String.trimAtEnd(): String {
  return ("x$this").trim().substring(1)
}

internal abstract class ExecSource @Inject constructor(private val execOperations: ExecOperations) :
  ValueSource<ExecSourceResult, Parameters> {
  interface Parameters : ValueSourceParameters {
    val args: ListProperty<String>
    @get:Optional val workingDir: RegularFileProperty
  }

  override fun obtain(): ExecSourceResult? {
    val arguments = parameters.args.get()
    val inputWorkingDir = parameters.workingDir.asFile.orNull
    val buffer = Buffer()
    buffer.outputStream().use { os ->
      try {
        execOperations.exec {
          configureExec(inputWorkingDir, arguments)
          standardOutput = os
          errorOutput = os
        }
      } catch (ignored: ExecException) {
        return null
      }
    }
    val value = buffer.readUtf8().trim { it <= ' ' }
    return ExecSourceResult(value)
  }
}

/**
 * A holder class for use with [ExecSource] that explicitly does not implement [equals] or
 * [hashCode] so that it does not invalidate the configuration cache.
 */
internal class ExecSourceResult(val value: String) {
  override fun equals(other: Any?): Boolean = true

  override fun hashCode(): Int = 0

  override fun toString(): String = "ExecSourceResult(value=$value)"
}
