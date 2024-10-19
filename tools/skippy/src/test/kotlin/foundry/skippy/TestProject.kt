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

import okio.BufferedSink
import okio.FileSystem
import okio.Path
import okio.buffer

class TestProject(
  private val fileSystem: FileSystem,
  private val rootPath: Path,
  private val gradlePath: String,
  body: TestProject.() -> Unit,
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
    content: String = "buildscript { repositories { mavenCentral() } }",
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

  fun projectFile(name: String, content: String) {
    fileSystem.write(projectPath / name) { writeUtf8(content) }
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
