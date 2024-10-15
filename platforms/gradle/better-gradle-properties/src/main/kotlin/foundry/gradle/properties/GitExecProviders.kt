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
package foundry.gradle.properties

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import javax.inject.Inject
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations

public fun ProviderFactory.gitExecProvider(vararg args: String): Provider<String> {
  require(args.isNotEmpty()) { "Args list is empty" }
  return of(GitExecValueSource::class.java) { parameters.args.addAll(*args) }
}

// Adapted from
// https://docs.gradle.org/8.1/userguide/configuration_cache.html#config_cache:requirements:external_processes
internal abstract class GitExecValueSource
@Inject
constructor(private val execOperations: ExecOperations) :
  ValueSource<String, GitExecValueSource.Parameters> {

  override fun obtain(): String {
    val args = parameters.args.get()
    check(args.isNotEmpty()) { "Args list is empty" }
    val output = ByteArrayOutputStream()
    execOperations.exec {
      commandLine(args)
      standardOutput = output
    }
    return String(output.toByteArray(), Charset.defaultCharset()).trim { it <= ' ' }
  }

  interface Parameters : ValueSourceParameters {
    val args: ListProperty<String>
  }
}
