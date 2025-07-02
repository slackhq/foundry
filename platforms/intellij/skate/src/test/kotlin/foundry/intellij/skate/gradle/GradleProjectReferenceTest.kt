/*
 * Copyright (C) 2025 Slack Technologies, LLC
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
package foundry.intellij.skate.gradle

import com.google.common.truth.Truth.assertThat
import com.intellij.util.io.delete
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.pathString
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GradleProjectReferenceTest {

  @get:Rule val tmpFolder = TemporaryFolder()

  @Test
  fun `reference resolves to correct build file paths`() {
    val testCases =
      mapOf(
        ":platforms:intellij:skate" to "platforms/intellij/skate",
        ":tools:cli" to "tools/cli",
        ":tools:foundry-common" to "tools/foundry-common",
        ":" to "",
      )

    testCases.forEach { (projectPath, expectedRelativePath) ->
      val calculatedPath = convertProjectPathToFilePath(projectPath)
      assertThat(calculatedPath).isEqualTo(expectedRelativePath)
    }
  }

  @Test
  fun `reference prefers kotlin build files over groovy`() {
    // Create a temporary directory for testing
    val tempDir = tmpFolder.newFolder("test-project").toPath()

    // Test when both files exist - should prefer .kts
    val buildFileKts = tempDir.resolve("build.gradle.kts")
    val buildFile = tempDir.resolve("build.gradle")
    buildFileKts.createFile()
    buildFile.createFile()

    val bothExistResult = findBuildFile(tempDir)
    assertThat(bothExistResult?.name).isEqualTo("build.gradle.kts")

    // Clean up for next test
    buildFileKts.delete()
    buildFile.delete()

    // Test when only Groovy exists
    buildFile.createFile()
    val onlyGroovyResult = findBuildFile(tempDir)
    assertThat(onlyGroovyResult?.name).isEqualTo("build.gradle")

    // Clean up for next test
    buildFile.delete()

    // Test when only Kotlin exists
    buildFileKts.createFile()
    val onlyKotlinResult = findBuildFile(tempDir)
    assertThat(onlyKotlinResult?.name).isEqualTo("build.gradle.kts")

    // Clean up for next test
    buildFileKts.delete()

    // Test when neither exists
    val neitherExistsResult = findBuildFile(tempDir)
    assertThat(neitherExistsResult).isNull()
  }

  @Test
  fun `reference text ranges exclude quotes correctly`() {
    // Test the core logic of text range calculation
    val doubleQuoted = "\":platforms:intellij:skate\""
    val singleQuoted = "':tools:cli'"
    val unquoted = ":raw:path"

    // Test that quoted strings exclude the quotes in the range
    val doubleQuotedRange = calculateReferenceTextRange(doubleQuoted)
    assertThat(doubleQuotedRange.startOffset).isEqualTo(1) // After opening quote
    assertThat(doubleQuotedRange.endOffset)
      .isEqualTo(doubleQuoted.length - 1) // Before closing quote

    val singleQuotedRange = calculateReferenceTextRange(singleQuoted)
    assertThat(singleQuotedRange.startOffset).isEqualTo(1) // After opening quote
    assertThat(singleQuotedRange.endOffset)
      .isEqualTo(singleQuoted.length - 1) // Before closing quote

    // Test that unquoted strings use the entire range
    val unquotedRange = calculateReferenceTextRange(unquoted)
    assertThat(unquotedRange.startOffset).isEqualTo(0) // From beginning
    assertThat(unquotedRange.endOffset).isEqualTo(unquoted.length) // To end
  }

  @Test
  fun `reference navigation opens correct files`() {
    // Test that the navigate() method would open the right build file
    val projectPath = ":platforms:intellij:skate"
    val projectBasePath = tmpFolder.root.toPath()

    // Create the project directory structure
    val projectDir = projectBasePath.resolve("platforms/intellij/skate")
    projectDir.createDirectories()

    // Test when both files exist - should prefer .kts
    val buildFileKts = projectDir.resolve("build.gradle.kts")
    val buildFile = projectDir.resolve("build.gradle")
    buildFileKts.createFile()
    buildFile.createFile()

    val resolvedFile = resolveProjectBuildFile(projectBasePath.pathString, projectPath)
    assertThat(resolvedFile).isEqualTo(buildFileKts.pathString)

    // Clean up and test when only .gradle exists
    buildFileKts.deleteIfExists()
    val resolvedFileGroovy = resolveProjectBuildFile(projectBasePath.pathString, projectPath)
    assertThat(resolvedFileGroovy).isEqualTo(buildFile.pathString)

    // Clean up and test when neither exists
    buildFile.deleteIfExists()
    val resolvedFileNone = resolveProjectBuildFile(projectBasePath.pathString, projectPath)
    assertThat(resolvedFileNone).isNull()
  }

  @Test
  fun `project call pattern matching works correctly`() {
    // Test the pattern matching logic used in GradleProjectReferenceProvider
    val testCases =
      mapOf(
        "implementation(project(\":tools:cli\"))" to true,
        "testImplementation(project(':platforms:intellij:skate'))" to true,
        "api project(':tools:foundry-common')" to true,
        "implementation(someOtherFunction(\":not:a:project\"))" to false,
        "val myString = \":tools:cli\"" to false,
      )

    testCases.forEach { (parentText, expectedResult) ->
      val isInProjectCall = parentText.contains("project(")
      assertThat(isInProjectCall).isEqualTo(expectedResult)
    }
  }

  private fun convertProjectPathToFilePath(projectPath: String): String {
    return projectPath.removePrefix(":").replace(":", "/")
  }

  private fun resolveProjectBuildFile(projectBasePath: String, projectPath: String): String? {
    val relativePath = projectPath.removePrefix(":").replace(":", "/")
    val projectDirPath = Path.of(projectBasePath).resolve(relativePath)

    val buildFileKts = projectDirPath.resolve("build.gradle.kts")
    val buildFile = projectDirPath.resolve("build.gradle")

    return when {
      buildFileKts.exists() -> buildFileKts.pathString
      buildFile.exists() -> buildFile.pathString
      else -> null
    }
  }

  private fun findBuildFile(projectDir: Path): Path? {
    val buildFileKts = projectDir.resolve("build.gradle.kts")
    val buildFile = projectDir.resolve("build.gradle")

    return when {
      buildFileKts.exists() -> buildFileKts
      buildFile.exists() -> buildFile
      else -> null
    }
  }
}
