package slack.gradle.avoidance

import okio.BufferedSink
import okio.FileSystem
import okio.Path
import okio.buffer

class TestProject(
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
