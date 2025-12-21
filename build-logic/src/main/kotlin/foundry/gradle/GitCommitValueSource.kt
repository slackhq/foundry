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
package foundry.gradle

import java.util.Locale
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/** A [ValueSource] that reads the current git commit hash from the .git directory. */
public abstract class GitCommitValueSource : ValueSource<String, GitCommitValueSource.Params> {

  public interface Params : ValueSourceParameters {
    public val rootDir: DirectoryProperty
  }

  override fun obtain(): String? {
    return try {
      val gitDir = parameters.rootDir.get().asFile.resolve(".git")
      val headFile = gitDir.resolve("HEAD")
      if (!headFile.exists()) return null

      val headContents = headFile.readText(Charsets.UTF_8).lowercase(Locale.US).trim()

      if (isGitHash(headContents)) {
        return headContents
      }

      if (!headContents.startsWith("ref:")) {
        return null
      }

      val headRef = headContents.removePrefix("ref:").trim()
      val refFile = gitDir.resolve(headRef)
      if (!refFile.exists()) {
        return null
      }

      refFile.readText(Charsets.UTF_8).trim().takeIf { isGitHash(it) }
    } catch (_: Exception) {
      null
    }
  }

  private fun isGitHash(hash: String): Boolean {
    return hash.length == 40 && hash.all { it in '0'..'9' || it in 'a'..'f' }
  }
}
