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
package foundry.cli.gradle

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.pair
import com.google.auto.service.AutoService
import eu.jrie.jetbrains.kotlinshell.shell.ScriptingShell
import eu.jrie.jetbrains.kotlinshell.shell.ShellScript
import foundry.cli.CommandFactory
import foundry.cli.dryRunOption
import foundry.cli.gradle.GradleTestFixturesMigratorCli.Companion.DESCRIPTION
import foundry.cli.multipleSet
import foundry.cli.projectDirOption
import foundry.cli.skipBuildAndCacheDirs
import foundry.cli.walkEachFile
import java.io.File
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolute
import kotlin.io.path.absolutePathString
import kotlin.io.path.appendLines
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.notExists
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.useLines
import kotlin.io.path.walk
import kotlin.io.path.writeText
import okio.blackholeSink
import okio.buffer

/** @see DESCRIPTION */
public class GradleTestFixturesMigratorCli : CliktCommand() {

  private companion object {
    const val DESCRIPTION =
      "A CLI migrates test-fixtures subprojects to use native gradle test fixtures."

    const val ANDROID_TEST_FIXTURES_BLOCK = "android.testFixtures.enable = true"
    const val JAVA_FIXTURES_BLOCK = "`java-test-fixtures`"
    val POINTLESS_TEST_FIXTURE_CONFIGURATIONS = setOf("testImplementation")
  }

  @AutoService(CommandFactory::class)
  public class Factory : CommandFactory {
    override val key: String = "migrate-gradle-test-fixtures"
    override val description: String = DESCRIPTION

    override fun create(): CliktCommand = GradleTestFixturesMigratorCli()
  }

  override fun help(context: Context): String = DESCRIPTION

  private val projectDir by projectDirOption()

  private val dryRun by dryRunOption()

  private val useFoundryDsl by option("--use-foundry").flag()

  private val targets by
    argument(
        "--targets",
        help =
          "The gradle-style project paths to test-fixture projects to migrate. " +
            "For example - :path:to:lib:test-fixtures",
      )
      .multiple()

  private val rawManualHostMapping by
    option(
        "--host-mapping",
        help =
          "Mapping of test fixture project _gradle paths_ to relative host project _directory paths_.",
      )
      .pair()
      .multipleSet()

  private val manualHostMapping by lazy {
    rawManualHostMapping.associate { (testFixtureGradlePath, hostPath) ->
      testFixtureGradlePath to projectDir.resolve(hostPath)
    }
  }

  private val ignoredProjects by
    option("--ignore", help = "Gradle paths of test fixture projects to ignore").multipleSet()

  internal data class Project(val gradlePath: String, val path: Path, val buildFile: Path) {
    @Suppress("DEPRECATION")
    val gradleAccessorPath =
      gradlePath.removePrefix(":").splitToSequence(':').joinToString(".", prefix = "projects.") {
        segment ->
        segment
          .splitToSequence('-')
          .joinToString("") { subsegment -> subsegment.capitalize(Locale.US) }
          .decapitalize(Locale.US)
      }

    val readme = path.resolve("README.md")
  }

  internal data class TestFixtureTarget(val hostProject: Project, val testFixtureProject: Project)

  @Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
  @ExperimentalPathApi
  override fun run() {
    val targetSet = targets.toSet()
    val settingsFile = projectDir.resolve("settings-all.gradle.kts")
    val projectByPath =
      projectDir
        .absolute()
        .walkEachFile { skipBuildAndCacheDirs() }
        .filter { it.name == "build.gradle.kts" }
        .filterNot { it.parent == projectDir }
        .associate { path -> // Get the gradle path relative to the root project dir as the key
          val projectPath = path.parent
          val gradlePath =
            ":" + projectPath.relativeTo(projectDir).toString().replace(File.separator, ":")
          projectPath to Project(gradlePath = gradlePath, path = projectPath, buildFile = path)
        }
        .toMutableMap()

    val migratableProjects =
      projectByPath.values
        .filter { it.buildFile.exists() }
        .filterNot { it.gradlePath in ignoredProjects }
        .mapNotNull { project ->
          if (
            project.gradlePath.endsWith(":test-fixtures") ||
              project.gradlePath.endsWith(":test-fixture")
          ) {
            if (!project.buildFile.parent.resolve("src/main").exists()) {
              echo("Test fixture project has no sources: ${project.gradlePath}", err = true)
              return@mapNotNull null
            }
            // Find the host project
            val hostProject = project.findHostProject(projectByPath) ?: return@mapNotNull null
            if (!hostProject.buildFile.exists()) return@mapNotNull null
            TestFixtureTarget(hostProject, project)
          } else {
            null
          }
        }
        .filter {
          if (targetSet.isEmpty()) {
            true
          } else {
            it.testFixtureProject.gradlePath in targetSet
          }
        }

    // Migrate test fixture sources and deps to their new host project
    var locMoved = 0L
    var dependenciesMoved = 0L
    for (migration in migratableProjects) {
      if (useFoundryDsl) {
        migration.validate()
      }
      migration.enableInBuildFile()
      dependenciesMoved += migration.moveDependencies()
      migration.moveReadmeContents()

      if (!dryRun) {
        migration.testFixtureProject.path.resolve("src/main").listDirectoryEntries().forEach {
          srcDir ->
          val targetPath =
            migration.hostProject.path.resolve("src/testFixtures/${srcDir.name}").apply {
              createParentDirectories()
            }
          if (targetPath.exists()) {
            if (targetPath.toFile().walkTopDown().filter { !it.isDirectory }.any()) {
              //              error("Test fixtures already exist in
              // ${migration.hostProject.gradlePath}")
            } else {
              targetPath.deleteRecursively()
            }
          }
          // Move with git so that history is protected
          val filesToMove =
            srcDir.walk().associate {
              val path = it
              val newDestination =
                targetPath.resolve(path.relativeTo(srcDir)).apply { createParentDirectories() }
              path.relativeTo(projectDir) to newDestination.relativeTo(projectDir)
            }
          blackholeSink().buffer().outputStream().use { blackHole ->
            for ((source, new) in filesToMove) {
              locMoved += (projectDir.resolve(source)).useLines { it.count() }
              shellInProject {
                val pipeline = pipeline { "git mv $source $new".process() pipe blackHole }
                pipeline.join()
              }
            }
          }
        }
        migration.testFixtureProject.path.deleteRecursively()
        projectByPath.remove(migration.testFixtureProject.path)
      } else {
        echo(
          "Moving sourced from ${migration.testFixtureProject.gradlePath} to ${migration.hostProject.gradlePath}"
        )
        echo("Deleting test-fixture project ${migration.testFixtureProject.gradlePath}")
      }
    }

    val allPathsToMigrate =
      migratableProjects.associate {
        it.testFixtureProject.gradleAccessorPath to it.hostProject.gradleAccessorPath
      }

    var updatedDependencyDeclarations = 0
    for (project in projectByPath.values) {
      var modified = false
      val lines = project.buildFile.readLines().toMutableList()
      val dependenciesIndex = lines.indexOfFirst { it.startsWith("dependencies {") }
      if (dependenciesIndex == -1) {
        // Nothing to do, no deps
        continue
      }

      @Suppress("LoopWithTooManyJumpStatements")
      for ((i, line) in lines.withIndex()) {
        if (i < dependenciesIndex) continue
        if (line.isBlank()) continue
        for ((old, new) in allPathsToMigrate) {
          if ("($old)" in line) {
            if (!line.contains("testImplementation(", ignoreCase = true)) {
              echo(
                "Suspicious non-test usage at ${project.buildFile.absolutePathString()}:${i + 1} ",
                err = true,
              )
            }
            if (new == project.gradleAccessorPath) {
              // Same project, just remove this line
              lines[i] = ""
            } else {
              updatedDependencyDeclarations++
              lines[i] = line.replace(old, "testFixtures($new)")
            }
            modified = true
          }
        }
      }
      if (modified) {
        if (!dryRun) {
          project.buildFile.writeText(lines.joinToString("\n"))
        } else {
          echo("Updating references in ${project.buildFile}")
        }
      }
    }

    // Finally - remove them from settings.gradle.kts
    val refsToRemove = migratableProjects.mapTo(mutableSetOf()) { it.testFixtureProject.gradlePath }
    if (!dryRun) {
      settingsFile.writeText(
        settingsFile
          .readLines()
          .filterNot { it.trim().removeSuffix(",").removeSurrounding("\"") in refsToRemove }
          .joinToString("\n")
      )
    }

    val stats =
      """
        Migration complete!
        Projects migrated: ${migratableProjects.size}
        Dependency declarations updated: $updatedDependencyDeclarations
        Lines of code moved: $locMoved
        Dependencies moved: $dependenciesMoved
        Projects ignored: ${ignoredProjects.size}
      """
        .trimIndent()

    echo(stats)
    projectDir.resolve("tmp/stats.txt").apply {
      createParentDirectories()
      writeText(stats)
    }
  }

  @Suppress("ReturnCount")
  private fun Project.findHostProject(mapping: Map<Path, Project>): Project? {
    manualHostMapping[gradlePath]?.let {
      return mapping.getValue(it)
    }
    val parentDir = path.parent
    // Prefer the API dir first
    val apiDir = parentDir.resolve("api")
    if (apiDir.resolve("src").exists()) {
      return mapping.getValue(apiDir)
    }
    if (parentDir.resolve("src").exists()) {
      return mapping.getValue(parentDir)
    }
    echo("Could not resolve host project for '$gradlePath'", err = true)
    return null
  }

  @OptIn(ExperimentalPathApi::class)
  private fun TestFixtureTarget.validate() {
    // Test fixture projects cannot currently work with certain tools like KSP
    val buildFile = testFixtureProject.buildFile.readText()
    val incompatibleExpressions = setOf("dagger()", "ksp(", "ksp)")
    for (expression in incompatibleExpressions) {
      if (expression in buildFile) {
        error(
          "Incompatible test-fixtures build file found: file://${testFixtureProject.buildFile} "
        )
      }
    }
  }

  @Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
  private fun TestFixtureTarget.enableInBuildFile() {
    val lines = hostProject.buildFile.readLines().toMutableList()

    if (useFoundryDsl) {
      if ("enableTestFixtures()" in hostProject.buildFile.readText()) {
        // already enabled, return
        return
      }
      // Find the `features {` block if any
      val featuresBlock =
        lines.indexOfFirst { it.contains("features {") && !it.contains("android {") }
      if (featuresBlock == -1) {
        // No features block. Check for `foundry {`
        val foundryBlock = lines.indexOfFirst { it.contains("foundry {") }
        if (foundryBlock == -1) {
          // No DSL at all, add one
          val endOfPluginsBlock = lines.indexOfFirst { it == "}" }
          check(endOfPluginsBlock != -1) { "No plugins block found in ${hostProject.gradlePath}" }
          lines.add(endOfPluginsBlock + 1, "foundry { features { enableTestFixtures() } }")
        } else {
          if (!lines[foundryBlock].endsWith("{")) {
            // There's other stuff on the line, split there and insert in between
            val (first, rest) = lines[foundryBlock].split("foundry {")
            lines[foundryBlock] = "${first}foundry {"
            lines.addAll(foundryBlock + 1, listOf("features { enableTestFixtures() }", rest))
          } else {
            lines.add(foundryBlock + 1, "features { enableTestFixtures() }")
          }
        }
      } else {
        if (!lines[featuresBlock].endsWith("{")) {
          // There's other stuff on the line, split there and insert in between
          val (first, rest) = lines[featuresBlock].split("features {")
          lines[featuresBlock] = "${first}features {"
          lines.addAll(featuresBlock + 1, listOf("enableTestFixtures()", rest))
        } else {
          lines.add(featuresBlock + 1, "enableTestFixtures()")
        }
      }
      if (!dryRun) {
        hostProject.buildFile.writeText(lines.joinToString("\n"))
      } else {
        echo("Enabling test fixtures on project ${hostProject.gradlePath}")
      }
    } else {
      if (lines.any { ANDROID_TEST_FIXTURES_BLOCK in it || JAVA_FIXTURES_BLOCK in it }) {
        // already enabled, return
        return
      }

      // If it's android, add android.testFixtures.enable = true after plugins
      // If it's jvm, add `java-test-fixtures` to the end of the plugins block
      val hostType = hostProject.type

      val fixturesType = testFixtureProject.type

      if (hostType == ProjectType.JVM && fixturesType == ProjectType.ANDROID) {
        error(
          "Cannot hoist Android fixtures in ${testFixtureProject.gradlePath} into JVM host ${hostProject.gradlePath}"
        )
      }

      val endOfPlugins = lines.indexOfFirst { it == "}" }
      check(endOfPlugins != -1) {
        error("Could not find end of plugins in ${hostProject.buildFile}")
      }
      when (hostType) {
        ProjectType.ANDROID -> {
          lines.add(endOfPlugins + 1, "\n$ANDROID_TEST_FIXTURES_BLOCK")
        }
        ProjectType.JVM -> {
          lines.add(endOfPlugins, JAVA_FIXTURES_BLOCK)
        }
      }
      if (!dryRun) {
        hostProject.buildFile.writeText(lines.joinToString("\n"))
      } else {
        echo("Enabling test fixtures on $hostType project ${hostProject.gradlePath}")
      }
    }
  }

  @Suppress("LongMethod", "ReturnCount", "CyclomaticComplexMethod")
  private fun TestFixtureTarget.moveDependencies(): Long {
    var dependenciesMoved = 0L
    var hasTestOnlyDep = false
    // Mapping of configuration to dependencies
    val dependencies = mutableMapOf<String, MutableSet<String>>()
    // Get dependencies from the test fixtures project
    val testFixtureLines = testFixtureProject.buildFile.readLines()
    val dependenciesIndex = testFixtureLines.indexOfFirst { it.startsWith("dependencies {") }
    if (dependenciesIndex == -1) {
      // Nothing to do, no deps
      return dependenciesMoved
    } else {
      val endOfDeps =
        testFixtureLines.subList(dependenciesIndex + 1, testFixtureLines.size).indexOfFirst {
          it == "}"
        }
      val linesToParse =
        if (endOfDeps == -1 && testFixtureLines[dependenciesIndex].endsWith("}")) {
          // It's a single-line block like
          // dependencies { api(libs.whatever) }
          listOf(
            testFixtureLines[dependenciesIndex].removePrefix("dependencies { ").removeSuffix(" }")
          )
        } else {
          check(endOfDeps != -1) { "Could not find end of deps in ${testFixtureProject.buildFile}" }
          testFixtureLines.subList(dependenciesIndex + 1, dependenciesIndex + 1 + endOfDeps)
        }
      linesToParse
        .filter { it.isNotBlank() }
        .filterNot { it.trimStart().startsWith("//") }
        // Ignore the host project as a dep
        .filterNot { "(${hostProject.gradleAccessorPath})" in it }
        .forEach { line ->
          // configuration(dependency)
          val trimmed = line.trim()
          if (trimmed.split(" ").size > 1) {
            echo(
              "Could not parse dependency line '$line' in ${testFixtureProject.buildFile}",
              err = true,
            )
            return@forEach
          }
          val configuration = trimmed.substringBefore('(')
          val dependency = trimmed.removePrefix(configuration).removePrefix("(").removeSuffix(")")
          dependencies.getOrPut(configuration, ::mutableSetOf) += dependency
          if (
            dependency.contains("junit") ||
              dependency.contains("truth") ||
              dependency.contains("test")
          ) {
            hasTestOnlyDep = true
          }
        }
    }

    if (!hasTestOnlyDep) {
      echo("Test fixture project has no test deps: ${testFixtureProject.gradlePath}", err = true)
    }

    if (dependencies.isEmpty()) return dependenciesMoved

    val newDeps =
      dependencies
        .filterKeys { key -> key !in POINTLESS_TEST_FIXTURE_CONFIGURATIONS }
        .mapKeys { (configuration, _) ->
          when (configuration) {
            "implementation" -> "testFixturesImplementation"
            "api" -> "testFixturesApi"
            else ->
              error(
                "Unrecognized configuration '$configuration' in ${testFixtureProject.gradlePath}"
              )
          }
        }
        .entries
        .flatMap { (configuration, deps) -> deps.map { "$configuration($it)" } }

    dependenciesMoved += newDeps.size

    val hostLines = hostProject.buildFile.readLines().toMutableList()
    var hostDependenciesIndex = hostLines.indexOfFirst { it.startsWith("dependencies {") }
    if (hostDependenciesIndex == -1) {
      // Nothing to do, no deps
      hostLines += "dependencies {"
      hostLines += "}"
      hostDependenciesIndex = hostLines.lastIndex - 1
    } else if (hostLines[hostDependenciesIndex].endsWith("}")) {
      // It's a single line block, extend the line down one
      hostLines[hostDependenciesIndex] = hostLines[hostDependenciesIndex].removeSuffix("}")
      hostLines.add("}")
    }

    hostLines.addAll(hostDependenciesIndex + 1, newDeps)
    if (!dryRun) {
      hostProject.buildFile.writeText(hostLines.joinToString("\n"))
    } else {
      echo("Migrating test fixture dependencies to '${hostProject.gradlePath}'")
    }

    return dependenciesMoved
  }

  private fun TestFixtureTarget.moveReadmeContents() {
    if (testFixtureProject.readme.notExists()) return

    if (hostProject.readme.notExists()) {
      if (!dryRun) {
        hostProject.readme.apply {
          createFile()
          writeText(
            """
            ${hostProject.path.name}
            ${"=".repeat(hostProject.path.name.length)}
          """
              .trimIndent()
          )
        }
        shellInProject {
          blackholeSink().buffer().outputStream().use { blackHole ->
            val pipeline = pipeline {
              "git add ${hostProject.readme.absolutePathString()}".process() pipe blackHole
            }
            pipeline.join()
          }
        }
      }
    }

    val testFixtureReadmeContent = testFixtureProject.readme.readText()

    if (!dryRun) {
      hostProject.readme.appendLines(
        buildList {
          add("")
          add("")
          addAll(testFixtureReadmeContent.lines())
        }
      )
      hostProject.readme.writeText(hostProject.readme.readText().trim() + "\n")
    }
  }

  private val Project.type
    get() =
      buildFile.useLines { lines ->
        lines.firstNotNullOfOrNull { line ->
          if ("libs.plugins.android." in line || "id(\"com.android.library\")" in line) {
            ProjectType.ANDROID
          } else if ("libs.plugins.kotlin.jvm" in line) {
            ProjectType.JVM
          } else {
            null
          }
        } ?: error("Could not resolve project type of '$gradlePath'")
      }

  private enum class ProjectType {
    ANDROID,
    JVM,
  }

  private fun shellInProject(script: ShellScript) {
    ScriptingShell(emptyMap(), projectDir.toFile()).shell(script)
  }
}
