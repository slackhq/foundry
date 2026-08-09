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
package foundry.gradle

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectIsolationFunctionalTest {

  @JvmField @Rule val temporaryFolder = TemporaryFolder()

  @Test
  fun `applies root conventions to base plugin projects with isolated projects`() {
    val projectDir = temporaryFolder.root
    projectDir.writeFixture()
    val arguments =
      listOf(
        ":one:verifyProjectIsolationConvention",
        ":two:verifyProjectIsolationConvention",
        "--configuration-cache",
        "--configuration-cache-problems=fail",
        "-Dorg.gradle.unsafe.isolated-projects=true",
        "--console=plain",
        "--stacktrace",
      )

    val first = projectDir.runner(arguments).build()
    assertThat(first.task(":one:verifyProjectIsolationConvention")).isNotNull()
    assertThat(first.task(":two:verifyProjectIsolationConvention")).isNotNull()
    assertThat(first.output).contains("Configuration cache entry stored.")

    val second = projectDir.runner(arguments).build()
    assertThat(second.task(":one:verifyProjectIsolationConvention")).isNotNull()
    assertThat(second.task(":two:verifyProjectIsolationConvention")).isNotNull()
    assertThat(second.output).contains("Reusing configuration cache.")
  }

  // The plugin-under-test metadata includes AGP through pluginUnderTestRuntimeClasspath.
  @Suppress("WithPluginClasspathUsage")
  private fun File.runner(arguments: List<String>): GradleRunner {
    return GradleRunner.create().withProjectDir(this).withPluginClasspath().withArguments(arguments)
  }

  private fun File.writeFixture() {
    val jdkVersion = System.getProperty("java.specification.version")
    write(
      "settings.gradle.kts",
      """
      rootProject.name = "project-isolation-fixture"
      include(":one", ":two")
      """,
    )
    write(
      "gradle/libs.versions.toml",
      """
      [versions]
      jdk = "$jdkVersion"
      kotlin = "2.3.0"
      """,
    )
    write(
      "gradle.properties",
      """
      foundry.auto-apply.sort-dependencies=false
      """,
    )
    write(
      "build.gradle.kts",
      """
      import foundry.gradle.configureFoundryProjects

      plugins {
        id("com.slack.foundry.root")
      }

      configureFoundryProjects {
        tasks.register("verifyProjectIsolationConvention")
      }
      """,
    )
    write(
      "one/build.gradle.kts",
      """
      plugins {
        id("com.slack.foundry.base")
      }
      """,
    )
    write(
      "two/build.gradle.kts",
      """
      plugins {
        id("com.slack.foundry.base")
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
