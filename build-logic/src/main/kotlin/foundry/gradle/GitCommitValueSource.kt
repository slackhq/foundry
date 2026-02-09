/*
 * Copyright (C) 2026 Slack Technologies, LLC
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
package foundry.gradle

import java.io.File
import java.util.concurrent.TimeUnit
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/** A [ValueSource] that executes `git rev-parse HEAD` to get the current commit hash. */
public abstract class GitCommitValueSource : ValueSource<String, GitCommitValueSource.Params> {

  public interface Params : ValueSourceParameters {
    public val rootDir: DirectoryProperty
  }

  override fun obtain(): String? {
    return getGitCommitHash(parameters.rootDir.get().asFile)
  }

  public companion object {
    /** Executes `git rev-parse HEAD` in the given [directory] and returns the commit hash. */
    public fun getGitCommitHash(directory: File): String? {
      return try {
        val process =
          ProcessBuilder("git", "rev-parse", "HEAD")
            .directory(directory)
            .redirectErrorStream(true)
            .start()

        val completed = process.waitFor(10, TimeUnit.SECONDS)
        if (!completed || process.exitValue() != 0) {
          return null
        }

        process.inputStream.bufferedReader().readText().trim().takeIf { it.isNotEmpty() }
      } catch (_: Exception) {
        null
      }
    }
  }
}
