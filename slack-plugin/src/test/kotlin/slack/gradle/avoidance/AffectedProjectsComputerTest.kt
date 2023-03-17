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
package slack.gradle.avoidance

import com.google.common.truth.Truth.assertThat
import com.jraska.module.graph.DependencyGraph
import okio.BufferedSink
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import okio.buffer
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import slack.gradle.util.SgpLogger

class AffectedProjectsComputerTest {

  // FakeFileSystem doesn't work because it doesn't support isRegularFile/isDirectory ops.
  @get:Rule val tmpFolder = TemporaryFolder()

  private val diagnostics = mutableMapOf<String, String>()
  private val diagnosticWriter = DiagnosticWriter { name, content -> diagnostics[name] = content() }
  private val fileSystem = FileSystem.SYSTEM
  private lateinit var rootDirPath: Path
  private lateinit var rootTestProject: TestProject

  @Before
  fun setup() {
    rootDirPath = tmpFolder.newFolder("project").toOkioPath()
    rootTestProject =
      TestProject(fileSystem, rootDirPath, ":") {
        buildFile()
        settingsFile()
      }
  }

  private fun createComputer(dependencyGraph: DependencyGraph, changedFilePaths: List<String>) =
    AffectedProjectsComputer(
      dependencyGraph = { dependencyGraph },
      changedFilePaths = createChangedFilePaths(changedFilePaths),
      rootDirPath = rootDirPath,
      debug = true,
      logger = SgpLogger.system(),
      diagnostics = diagnosticWriter,
    )

  @Test
  fun `singular graph with no changes and no files is empty`() {
    createComputer(
        dependencyGraph = DependencyGraph.createSingular(":foo"),
        changedFilePaths = emptyList(),
      )
      .assertEmptyCompute()
  }

  @Test
  fun `singular graph with singular change`() {
    val projectName = "foo"
    newSubProject(projectName) {
      buildFile()
      sourceFile("Example.kt", "class Example")
    }
    createComputer(
        dependencyGraph = DependencyGraph.createSingular(":$projectName"),
        changedFilePaths = listOf("$projectName/src/main/kotlin/com/example/Example.kt"),
      )
      .assertComputed(
        expectedAffectedProjects = listOf(":$projectName"),
        expectedFocusProjects = listOf(":$projectName"),
      )
  }

  // TODO
  //  - Debug logging
  //  - Include filters
  //  - Never skip filters
  //  - Non-existent file, non-existent project (settings must change)
  //  - Focus file outputs
  //  - ???
  //  - Gradle tests
  //    - test fixtures

  private fun createChangedFilePaths(paths: List<String>) = paths.map { it.toPath() }.toList()

  private fun newSubProject(name: String, body: TestProject.() -> Unit): TestProject {
    return rootTestProject.subproject(name, body)
  }
}

private fun AffectedProjectsComputer.assertNullCompute() = compute().assertNull()

private fun AffectedProjectsComputer.assertEmptyCompute() = compute().assertEmpty()

private fun AffectedProjectsComputer.assertComputed(
  expectedAffectedProjects: List<String>,
  expectedFocusProjects: List<String>,
) {
  val result = compute().checkNotNull()
  assertThat(result.affectedProjects).containsExactlyElementsIn(expectedAffectedProjects)
  assertThat(result.focusProjects).containsExactlyElementsIn(expectedFocusProjects)
}

private fun AffectedProjectsResult?.checkNotNull() =
  checkNotNull(this) { "Expected result to be non-null" }

private fun AffectedProjectsResult?.assertNull() {
  check(this == null) { "Expected result to be null" }
}

private fun AffectedProjectsResult?.assertEmpty() {
  val result = checkNotNull()
  assertThat(result.affectedProjects).isEmpty()
  assertThat(result.focusProjects).isEmpty()
}

private class TestProject(
  private val fileSystem: FileSystem,
  private val rootPath: Path,
  private val gradlePath: String,
  body: TestProject.() -> Unit
) {
  private val relativePath = gradlePath.removePrefix(":").replace(":", Path.DIRECTORY_SEPARATOR)
  private val projectPath = rootPath / relativePath
  private val sourcePath =
    projectPath / "src" / "main" / "kotlin" / "com" / "example" / relativePath
  private var settingsFile: Path? = null

  val subprojects = mutableListOf<TestProject>()

  init {
    // Creates the project and source dirs
    fileSystem.createDirectories(projectPath)
    body()
  }

  fun subproject(gradlePath: String, body: TestProject.() -> Unit): TestProject {
    val project = TestProject(fileSystem, rootPath, gradlePath, body)
    subprojects += project
    appendToSettings(gradlePath)
    return project
  }

  fun buildFile(
    isKts: Boolean = false,
    content: String = "buildscript { repositories { mavenCentral() } }"
  ) {
    buildFile(isKts) { writeUtf8(content) }
  }

  fun buildFile(isKts: Boolean = false, body: BufferedSink.() -> Unit) {
    val name = if (isKts) "build.gradle.kts" else "build.gradle"
    fileSystem.write(projectPath / name, writerAction = body)
  }

  private fun ensureSourcePath() {
    if (!fileSystem.exists(sourcePath)) {
      fileSystem.createDirectories(sourcePath)
    }
  }

  fun sourceFile(name: String, content: String) {
    sourceFile(name) { writeUtf8(content) }
  }

  fun sourceFile(name: String, body: BufferedSink.() -> Unit) {
    ensureSourcePath()
    fileSystem.write(sourcePath / name, writerAction = body)
  }

  fun settingsFile(isKts: Boolean = false, vararg includes: String) {
    settingsFile(isKts) { writeUtf8(includes.joinToString("\n") { "include(\"$it\")" }) }
  }

  fun appendToSettings(gradlePath: String) {
    settingsFile?.let {
      fileSystem.appendingSink(it, mustExist = true).buffer().use {
        it.writeUtf8("\ninclude(\"$gradlePath\")")
      }
    }
  }

  fun settingsFile(isKts: Boolean = false, body: BufferedSink.() -> Unit) {
    val name = if (isKts) "settings.gradle.kts" else "settings.gradle"
    val path = projectPath / name
    fileSystem.write(path, writerAction = body)
    settingsFile = path
  }
}
