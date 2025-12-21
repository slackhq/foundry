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

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GitCommitValueSourceTest {

  @get:Rule val tempFolder = TemporaryFolder()

  @Test
  fun `returns commit hash for valid git repo`() {
    val gitDir = tempFolder.newFolder("git-repo")
    gitDir.initGitRepo()
    gitDir.gitCommit("Initial commit")

    val result = GitCommitValueSource.getGitCommitHash(gitDir)

    assertThat(result).isNotNull()
    assertThat(result).hasLength(40)
    assertThat(result).matches("[0-9a-f]{40}")
  }

  @Test
  fun `returns null for non-git directory`() {
    val nonGitDir = tempFolder.newFolder("not-a-repo")

    val result = GitCommitValueSource.getGitCommitHash(nonGitDir)

    assertThat(result).isNull()
  }

  @Test
  fun `returns null for non-existent directory`() {
    val nonExistentDir = File(tempFolder.root, "does-not-exist")

    val result = GitCommitValueSource.getGitCommitHash(nonExistentDir)

    assertThat(result).isNull()
  }

  @Test
  fun `returns correct hash after multiple commits`() {
    val gitDir = tempFolder.newFolder("multi-commit-repo")
    gitDir.initGitRepo()
    gitDir.gitCommit("First commit")
    val firstHash = GitCommitValueSource.getGitCommitHash(gitDir)

    gitDir.gitCommit("Second commit")
    val secondHash = GitCommitValueSource.getGitCommitHash(gitDir)

    assertThat(firstHash).isNotNull()
    assertThat(secondHash).isNotNull()
    assertThat(firstHash).isNotEqualTo(secondHash)
  }

  @Test
  fun `works with packed refs`() {
    val gitDir = tempFolder.newFolder("packed-refs-repo")
    gitDir.initGitRepo()
    gitDir.gitCommit("Initial commit")

    // Pack refs to simulate a cloned repo
    gitDir.git("gc")

    val result = GitCommitValueSource.getGitCommitHash(gitDir)

    assertThat(result).isNotNull()
    assertThat(result).hasLength(40)
  }

  @Test
  fun `works with detached HEAD`() {
    val gitDir = tempFolder.newFolder("detached-head-repo")
    gitDir.initGitRepo()
    gitDir.gitCommit("First commit")
    val firstHash = GitCommitValueSource.getGitCommitHash(gitDir)!!
    gitDir.gitCommit("Second commit")

    // Detach HEAD by checking out a specific commit
    gitDir.git("checkout", firstHash)

    val result = GitCommitValueSource.getGitCommitHash(gitDir)

    assertThat(result).isEqualTo(firstHash)
  }

  private fun File.initGitRepo() {
    git("init")
    git("config", "user.email", "test@example.com")
    git("config", "user.name", "Test User")
  }

  private fun File.gitCommit(message: String) {
    // Create or modify a file to have something to commit
    File(this, "file-${System.nanoTime()}.txt").writeText("content")
    git("add", ".")
    git("commit", "-m", message)
  }

  private fun File.git(vararg args: String) {
    val process =
      ProcessBuilder("git", *args)
        .directory(this)
        .redirectErrorStream(true)
        .start()
    process.waitFor()
    check(process.exitValue() == 0) {
      "git ${args.joinToString(" ")} failed: ${process.inputStream.bufferedReader().readText()}"
    }
  }
}
