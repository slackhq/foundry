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
package foundry.gradle.stats

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModuleStatsProjectIsolationFunctionalTest {

  @JvmField @Rule val temporaryFolder = TemporaryFolder()

  @Test
  fun `android library loc depends on compile release sources with isolated projects`() {
    val projectDir = temporaryFolder.root
    projectDir.writeAndroidLibraryFixture()
    val projectCacheDir = temporaryFolder.newFolder("project-cache")
    val arguments =
      listOf(
        ":aggregateModuleStats",
        ":library:loc",
        "--configuration-cache",
        "--configuration-cache-problems=fail",
        "--isolated-projects",
        "-Dorg.gradle.projectcachedir=${projectCacheDir.absolutePath}",
        "--console=plain",
        "--stacktrace",
      )

    val first = projectDir.runner(arguments).build()
    assertThat(first.task(":library:compileReleaseSources")).isNotNull()
    assertThat(first.task(":library:loc")).isNotNull()
    assertThat(first.task(":library:moduleStats")).isNotNull()
    assertThat(first.task(":aggregateModuleStats")).isNotNull()
    assertThat(first.output).contains("Configuration cache entry stored.")

    val second = projectDir.runner(arguments).build()
    assertThat(second.task(":library:compileReleaseSources")).isNotNull()
    assertThat(second.task(":library:loc")).isNotNull()
    assertThat(second.task(":library:moduleStats")).isNotNull()
    assertThat(second.task(":aggregateModuleStats")).isNotNull()
    assertThat(second.output).contains("Reusing configuration cache.")
    assertThat(second.output).contains("Configuration cache entry reused.")
  }

  @Test
  fun `task-name dependency reports a missing compile task clearly`() {
    val projectDir = temporaryFolder.root
    projectDir.writeMissingCompileTaskFixture()
    val projectCacheDir = temporaryFolder.newFolder("project-cache")

    val result =
      projectDir
        .runner(
          listOf(
            ":library:loc",
            "--configuration-cache",
            "--configuration-cache-problems=fail",
            "--isolated-projects",
            "-Dorg.gradle.projectcachedir=${projectCacheDir.absolutePath}",
            "--console=plain",
            "--stacktrace",
          )
        )
        .buildAndFail()

    assertThat(result.output)
      .contains("Task with name 'compileReleaseSources' not found in project ':library'")
  }

  @Suppress("WithPluginClasspathUsage")
  private fun File.runner(arguments: List<String>): GradleRunner {
    return GradleRunner.create().withProjectDir(this).withPluginClasspath().withArguments(arguments)
  }

  private fun File.writeAndroidLibraryFixture() {
    writeCommonFixture()
    write(
      "library/build.gradle.kts",
      """
      plugins {
        id("com.android.library")
        id("com.slack.foundry.base")
      }

      android {
        namespace = "foundry.module.stats.fixture"
        compileSdk = 36
      }
      """,
    )
    write(
      "library/src/main/java/foundry/module/stats/fixture/Library.java",
      """
      package foundry.module.stats.fixture;

      public final class Library {}
      """,
    )
  }

  private fun File.writeMissingCompileTaskFixture() {
    writeCommonFixture()
    write(
      "library/build.gradle.kts",
      """
      plugins {
        id("com.slack.foundry.base")
      }

      tasks.register("loc") {
        dependsOn("compileReleaseSources")
      }
      """,
    )
  }

  private fun File.writeCommonFixture() {
    val jdkVersion = System.getProperty("java.specification.version")
    write(
      "settings.gradle.kts",
      """
      rootProject.name = "module-stats-project-isolation-fixture"
      include(":library")

      dependencyResolutionManagement {
        repositories {
          google()
          mavenCentral()
        }
      }
      """,
    )
    write(
      "gradle/libs.versions.toml",
      """
      [versions]
      jdk = "$jdkVersion"
      kotlin = "2.3.0"

      [libraries]
      google-coreLibraryDesugaring = "com.android.tools:desugar_jdk_libs:2.1.5"
      """,
    )
    write(
      "gradle.properties",
      """
      foundry.auto-apply.sort-dependencies=false
      foundry.android.compileSdkVersion=36
      foundry.android.minSdkVersion=23
      foundry.android.targetSdkVersion=36
      foundry.auto-apply.cache-fix=false
      foundry.auto-apply.detekt=false
      foundry.modscore.enabled=true
      """,
    )
    write(
      "build.gradle.kts",
      """
      plugins {
        id("com.slack.foundry.root")
      }
      """,
    )
  }

  private fun File.write(path: String, content: String) {
    val destination = resolve(path)
    destination.parentFile.mkdirs()
    destination.writeText(content.trimIndent().trim() + "\n")
  }
}
