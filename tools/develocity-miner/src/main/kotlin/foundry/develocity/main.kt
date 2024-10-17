/*
 * Copyright (C) 2024 Slack Technologies, LLC
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
package foundry.develocity

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import kotlin.system.exitProcess
import okio.Path.Companion.toOkioPath
import retrofit2.HttpException

internal class DevelocityMinerCli : SuspendingCliktCommand() {
  private val apiUrl by option("--api-url", envvar = "DEVELOCITY_API_URL").required()
  private val apiToken by option("--api-token", envvar = "DEVELOCITY_API_TOKEN").required()
  private val logLevel by option("--log-level").default("off")
  private val apiCache by option("--api-cache-dir").path(canBeFile = false, mustExist = false)
  private val queries by option("--query").multiple()

  override suspend fun run() {
    val status =
      try {
        DevelocityMiner(
            apiUrl = apiUrl,
            apiToken = apiToken,
            apiCache = apiCache?.toOkioPath(),
            logLevel = logLevel,
            requestedQueries = queries.toSet(),
          )
          .run()
        0
      } catch (e: Exception) {
        e.printStackTrace()
        if (e is HttpException) {
          e.response()?.body()?.let { System.err.println(it) }
        }
        1
      }
    exitProcess(status)
  }
}

public suspend fun main(args: Array<String>) {
  DevelocityMinerCli()
    .main(
      args +
        arrayOf(
          "--api-url",
          "<TODO>",
          "--api-token",
          "<TODO>",
          "--log-level",
          "trace",
          "--api-cache-dir",
          "<TODO>/.cache/develocity-api-cache",
        )
    )
}
