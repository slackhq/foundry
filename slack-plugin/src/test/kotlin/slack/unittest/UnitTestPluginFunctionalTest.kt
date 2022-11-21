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
package slack.unittest

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@Ignore(
  "Sometimes failing locally: https://slack-pde.slack.com/archives/C8EHYHTKP/p1584549515416500"
)
class UnitTestPluginFunctionalTest {

  @get:Rule val testProjectDir = TemporaryFolder.builder().assureDeletion().build()

  private lateinit var settingsFile: File
  private lateinit var buildFile: File
  private lateinit var propertiesFile: File
  private lateinit var androidManifest: File

  @Before
  fun setup() {
    settingsFile = testProjectDir.newFile("settings.gradle")
    buildFile = testProjectDir.newFile("build.gradle")
    propertiesFile = testProjectDir.newFile("gradle.properties")

    val srcMain = testProjectDir.newFolder("src", "main")
    androidManifest = File(srcMain, "AndroidManifest.xml")
  }

  @Test
  fun `Applying the plugin works on a java project`() {
    // language=gradle
    buildFile.writeText(
      """
          plugins {
            id 'slack.unit-test'
            id 'java'
          }
          repositories {
            jcenter()
          }
      """
        .trimIndent()
    )

    val runner = GradleRunner.create().withProjectDir(testProjectDir.root).withPluginClasspath()

    val result = runner.withArguments("ciUnitTest", "--dry-run").build()

    assertThat(result.output).apply {
      contains("BUILD SUCCESSFUL")
      contains(":test SKIPPED")
      contains(":ciUnitTest SKIPPED")
    }
  }

  @Test
  fun `Plugin creates the correct tasks on an Android application`() {
    setupAndroid()

    val runner = GradleRunner.create().withProjectDir(testProjectDir.root).withPluginClasspath()

    val result = runner.withArguments("ciUnitTest", "--dry-run").build()

    assertThat(result.output).apply {
      contains("BUILD SUCCESSFUL")
      contains(":testDebugUnitTest SKIPPED")
      contains(":ciUnitTest SKIPPED")
    }
  }

  @Test
  fun `Plugin creates the correct tasks on an Android application with product flavors`() {
    setupAndroid(productFlavors = true)

    val runner = GradleRunner.create().withProjectDir(testProjectDir.root).withPluginClasspath()

    val result = runner.withArguments("ciUnitTest", "--dry-run").build()

    assertThat(result.output).apply {
      contains("BUILD SUCCESSFUL")
      contains(":testInternalDebugUnitTest SKIPPED")
      contains(":ciUnitTest SKIPPED")
    }
  }

  @Test
  fun `Using property opts out of CI task creation`() {
    setupAndroid()
    propertiesFile.writeText("slack.ci-unit-test.enable=false")

    val runner = GradleRunner.create().withProjectDir(testProjectDir.root).withPluginClasspath()

    val result = runner.withArguments("tasks").build()

    assertThat(result.output).apply {
      contains("BUILD SUCCESSFUL")
      doesNotContain("ciUnitTest")
    }
  }

  @Test
  fun `Plugin fails build if unexpected flavors are present`() {
    setupAndroid(productFlavors = true, flavorNameOne = "hello", flavorNameTwo = "goodbye")

    val runner = GradleRunner.create().withProjectDir(testProjectDir.root).withPluginClasspath()

    val result = runner.withArguments("tasks").buildAndFail()

    assertThat(result.output).contains("Failed to create \"ciUnitTest\"")
  }

  private fun setupAndroid(
    productFlavors: Boolean = false,
    flavorNameOne: String = "internal",
    flavorNameTwo: String = "external"
  ) {
    val flavorBlock =
      if (productFlavors) {
        // language=gradle
        """
        flavorDimensions "environment"
        productFlavors {
            $flavorNameOne {
                dimension "environment"
            }
            $flavorNameTwo {
                dimension "environment"
            }
        }
      """
          .trimIndent()
      } else {
        ""
      }

    // language=gradle
    buildFile.writeText(
      """
          plugins {
              id 'com.android.application'
              id 'slack.unit-test'
          }
          repositories {
              google()
              jcenter()
              mavenCentral()
          }
          android {
              compileSdkVersion 28
              defaultConfig {
                  applicationId "test.app"
                  versionName "test-version-name"
                  versionCode 9000
                  minSdkVersion 28
                  targetSdkVersion 28
              }
              $flavorBlock
          }
      """
        .trimIndent()
    )
    // language=xml
    androidManifest.writeText(
      """<?xml version="1.0" encoding="utf-8"?>
            <manifest package="test.app"
              xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:tools="http://schemas.android.com/tools">
              <application android:name=".TestApp"/>
            </manifest>
      """
        .trimIndent()
    )
  }
}
