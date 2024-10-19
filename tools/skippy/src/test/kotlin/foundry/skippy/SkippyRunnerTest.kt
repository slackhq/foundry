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
package foundry.skippy

import com.google.common.truth.Truth.assertThat
import com.jraska.module.graph.DependencyGraph
import com.squareup.moshi.Moshi
import foundry.common.FoundryLogger
import foundry.common.readLines
import foundry.common.writeLines
import foundry.skippy.SkippyConfig.Companion.GLOBAL_TOOL
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.fakefilesystem.FakeFileSystem
import org.junit.Before
import org.junit.Test

class SkippyRunnerTest {

  private val fs = FakeFileSystem()
  private lateinit var rootDirPath: Path
  private lateinit var rootTestProject: TestProject

  @Before
  fun setup() {
    rootDirPath = fs.workingDirectory / "project"
    fs.createDirectory(rootDirPath)
    rootTestProject =
      TestProject(fs, rootDirPath, ":") {
        buildFile()
        settingsFile()
      }
  }

  private fun runSkippy(
    dependencyGraph: DependencyGraph,
    changedFilePaths: List<String>,
    configs: List<SkippyConfig> = listOf(SkippyConfig(GLOBAL_TOOL, buildUponDefaults = true)),
  ): Unit = runTest {
    val changedFilesPath = rootDirPath / "changed_files.txt"
    changedFilesPath.writeLines(changedFilePaths, fs)
    SkippyRunner(
        rootDir = rootDirPath,
        outputsDir = rootDirPath / "build" / "skippy" / "outputs",
        androidTestProjects = setOf(":foo"),
        dependencyGraph = dependencyGraph.serializableGraph(),
        changedFilesPath = changedFilesPath,
        originalConfigMap = configs.associateBy { it.tool },
        moshi = Moshi.Builder().build(),
        debug = true,
        logger = FoundryLogger.system(),
        mergeOutputs = true,
        fs = fs,
      )
      .run(coroutineContext)
  }

  @Test
  fun `single config should only write global and merged`() {
    runSkippy(
      dependencyGraph = DependencyGraph.createSingular(":foo"),
      changedFilePaths = emptyList(),
    )

    assertThat(fs.exists(rootDirPath.resolve("build/skippy/outputs/merged/affected_projects.txt")))
      .isTrue()
    assertThat(fs.exists(rootDirPath.resolve("build/skippy/outputs/global/affected_projects.txt")))
      .isTrue()
  }

  @Test
  fun `multi config writes multiple and merged`() {
    runSkippy(
      dependencyGraph = DependencyGraph.createSingular(":foo"),
      changedFilePaths = emptyList(),
      configs = listOf(SkippyConfig(GLOBAL_TOOL, buildUponDefaults = true), SkippyConfig("lint")),
    )

    assertThat(fs.exists(rootDirPath.resolve("build/skippy/outputs/merged/affected_projects.txt")))
      .isTrue()
    assertThat(fs.exists(rootDirPath.resolve("build/skippy/outputs/lint/affected_projects.txt")))
      .isTrue()
    assertThat(fs.exists(rootDirPath.resolve("build/skippy/outputs/global/affected_projects.txt")))
      .isFalse()
  }

  @Test
  fun `simple change with multiple tools that affects all tools`() {
    val projectName = "foo"
    newSubProject(projectName) {
      buildFile()
      sourceFile("Example.kt", "class Example")
    }
    runSkippy(
      dependencyGraph = DependencyGraph.createSingular(":$projectName"),
      changedFilePaths = listOf("$projectName/src/main/kotlin/com/example/Example.kt"),
      configs = listOf(SkippyConfig(GLOBAL_TOOL, buildUponDefaults = true), SkippyConfig("lint")),
    )

    // Both lint and merged should have the same affected projects
    for (tool in listOf("lint", "merged")) {
      assertComputed(
        tool,
        expectedAffectedProjects = listOf(":$projectName"),
        expectedFocusProjects = listOf(":$projectName"),
        expectedAffectedAndroidTestProjects = listOf(":$projectName"),
      )
    }
  }

  @Test
  fun `simple change with multiple tools that affects only affects some tools`() {
    val fooProject = "foo"
    newSubProject(fooProject) {
      buildFile()
      sourceFile("Example.kt", "class Example")
    }
    val barProject = "bar"
    newSubProject(barProject) {
      buildFile()
      projectFile("lint-baseline.xml", "<lint-baseline></lint-baseline>")
    }

    runSkippy(
      dependencyGraph = DependencyGraph.create(":$fooProject" to ":$barProject"),
      changedFilePaths = listOf("$barProject/lint-baseline.xml"),
      configs =
        listOf(
          SkippyConfig(GLOBAL_TOOL, buildUponDefaults = true),
          SkippyConfig("unitTest"),
          SkippyConfig("lint").let {
            it.copy(_includePatterns = it.includePatterns + "**/lint-baseline.xml")
          },
        ),
    )

    // Both lint and merged should have the same affected projects
    for (tool in listOf("lint", "merged")) {
      assertComputed(
        tool,
        expectedAffectedProjects = listOf(":$barProject"),
        expectedFocusProjects = listOf(":$barProject"),
        // Lint xml file doesn't affect android tests
        expectedAffectedAndroidTestProjects = emptyList(),
      )
    }
    assertComputed(
      "unitTest",
      expectedAffectedProjects = emptyList(),
      expectedFocusProjects = emptyList(),
      expectedAffectedAndroidTestProjects = emptyList(),
    )
  }

  private fun skippyOutput(tool: String, fileName: String) =
    rootDirPath.resolve("build/skippy/outputs/$tool/$fileName")

  private fun assertComputed(
    tool: String,
    expectedAffectedProjects: List<String>,
    expectedFocusProjects: List<String>,
    expectedAffectedAndroidTestProjects: List<String>,
  ) {
    val includedProjects = expectedFocusProjects.map { "include(\"$it\")" }
    assertThat(skippyOutput(tool, SkippyOutput.AFFECTED_PROJECTS_FILE_NAME).readLines(fs))
      .containsExactlyElementsIn(expectedAffectedProjects)
    assertThat(skippyOutput(tool, SkippyOutput.FOCUS_SETTINGS_FILE_NAME).readLines(fs))
      .containsExactlyElementsIn(includedProjects)
    assertThat(
        skippyOutput(tool, SkippyOutput.AFFECTED_ANDROID_TEST_PROJECTS_FILE_NAME).readLines(fs)
      )
      .containsExactlyElementsIn(expectedAffectedAndroidTestProjects)
  }

  private fun newSubProject(name: String, body: TestProject.() -> Unit): TestProject {
    return rootTestProject.subproject(name, body)
  }
}
