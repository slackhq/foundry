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
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.fakefilesystem.FakeFileSystem
import org.junit.Before
import org.junit.Test
import slack.gradle.util.SgpLogger
import slack.gradle.util.writeLines

class SkippyRunnerTest {

  private val diagnostics = mutableMapOf<String, String>()
  private val diagnosticWriter = DiagnosticWriter { tool, name, content ->
    diagnostics[tool + name] = content()
  }
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
    configs: List<SkippyConfig> = listOf(SkippyConfig(SkippyExtension.GLOBAL_TOOL)),
  ) = runTest {
    val changedFilesPath = rootDirPath / "changed_files.txt"
    changedFilesPath.writeLines(changedFilePaths, fs)
    SkippyRunner(
        rootDir = rootDirPath,
        outputsDir = rootDirPath / "build" / "skippy" / "outputs",
        diagnosticsDir = rootDirPath / "build" / "skippy" / "diagnostics",
        androidTestProjects = setOf(":foo"),
        dependencyGraph = dependencyGraph.serializableGraph(),
        changedFilesPath = changedFilesPath,
        originalConfigMap = configs.associateBy { it.tool },
        debug = true,
        logger = SgpLogger.system(),
        mergeOutputs = true,
        diagnostics = diagnosticWriter,
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
      configs = listOf(SkippyConfig(SkippyExtension.GLOBAL_TOOL), SkippyConfig("lint"))
    )

    assertThat(fs.exists(rootDirPath.resolve("build/skippy/outputs/merged/affected_projects.txt")))
      .isTrue()
    assertThat(fs.exists(rootDirPath.resolve("build/skippy/outputs/lint/affected_projects.txt")))
      .isTrue()
    assertThat(fs.exists(rootDirPath.resolve("build/skippy/outputs/global/affected_projects.txt")))
      .isFalse()
  }

  // TODO
  //  - Single global
  //  - Multiple tools global
  //  - Merged outputs
  //  - individual outputs
}
