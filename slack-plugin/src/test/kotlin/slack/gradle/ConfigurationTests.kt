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
package slack.gradle

import com.squareup.kotlinpoet.FileSpec
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import slack.gradle.util.newDir
import slack.gradle.util.newFile
import slack.gradle.util.newTemporaryFolder

@RunWith(Parameterized::class)
class ConfigurationTests(private val projectType: ProjectType) {

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun data(): List<Array<*>> {
      return listOf(*ProjectType.values().map { arrayOf(it) }.toTypedArray())
    }
  }

  @Rule @JvmField val tmpFolder = newTemporaryFolder()

  @Test
  fun configureProject() {
    val projectDir = prepareProject()
    runGradle(projectDir)
  }

  private fun prepareProject(): File {
    // Create fixture
    val projectDir = tmpFolder.newFolder("testProject")
    val buildFile = projectDir.newFile("build.gradle.kts")
    FileSpec.scriptBuilder("build.gradle.kts")
      .apply {
        beginControlFlow("plugins")
        addStatement("id(\"com.slack.gradle.base\")")
        for (plugin in projectType.plugins) {
          addStatement(plugin)
        }
        endControlFlow()
        addStatement("")
        if (projectType.isAndroid) {
          beginControlFlow("android")
          addStatement("namespace = \"sgp.test\"")
          endControlFlow()
        }
      }
      .build()
      .writeTo(buildFile)

    val mainSources = projectDir.newDir("src/main/java")

    return projectDir
  }

  private fun runGradle(projectDir: File, vararg args: String): BuildResult {
    // Run twice to properly ensure config cache worked
    val result = runGradleInternal(projectDir, *args)
    val cachedResult = runGradleInternal(projectDir, *args)
    require(cachedResult.output.contains("Reusing configuration cache."))
    return result
  }

  private fun runGradleInternal(projectDir: File, vararg args: String): BuildResult {
    val extraArgs = args.toMutableList()
    extraArgs += "--stacktrace"
    extraArgs += "--configuration-cache"
    return GradleRunner.create()
      .forwardStdOutput(System.out.writer())
      .forwardStdError(System.err.writer())
      .withProjectDir(projectDir)
      .withArguments(extraArgs)
      .withPluginClasspath()
      .withDebug(true) // Tests run in-process and way faster with this enabled
      .build()
  }

  enum class ProjectType(val isAndroid: Boolean, vararg val plugins: String) {
    JAVA(isAndroid = false, "`java-library`"),
    KOTLIN(isAndroid = false, "kotlin(\"jvm\")"),
    JAVA_ANDROID(isAndroid = true, "`java-library`", "id(\"com.android.library\")"),
    KOTLIN_ANDROID_LIBRARY(isAndroid = true, "kotlin(\"android\")", "id(\"com.android.library\")"),
    KOTLIN_ANDROID_APPLICATION(
      isAndroid = true,
      "kotlin(\"android\")",
      "id(\"com.android.application\")"
    ),
    KOTLIN_ANDROID_TEST(isAndroid = true, "kotlin(\"android\")", "id(\"com.android.test\")"),
    // TODO toe-hold for the future
    //    KOTLIN_MULTIPLATFORM(isAndroid = true, "kotlin(\"multiplatform\")"),
  }
}
