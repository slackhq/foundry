/*
 * Copyright (C) 2024 Slack Technologies, LLC
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
package com.slack.sgp.intellij.circuitgenerator

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Path
import junit.framework.TestCase

class GradleDependencyManagementTest : BasePlatformTestCase() {

  fun getFakeGradleBuildModel(): GradleBuildModel? {
    val testGradlePath = "src/test/resources/test_build.gradle.kts"
    val gradlePath = Path.of(this.basePath, testGradlePath)
    val gradleFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(gradlePath)
    gradleFile?.let {
      return ProjectBuildModel.get(project).getModuleBuildModel(it)
    }
    return null
  }

  fun testAddParcelizeImport() {
    val gradleBuildModel = getFakeGradleBuildModel()
    val pluginBlocks = GradleDependencyManager().addParcelizeImport(gradleBuildModel, project)
    TestCase.assertTrue("alias(libs.plugins.kotlin.plugin.parcelize)" in pluginBlocks!!.text)
  }

  fun testAddCircuitImport() {
    val gradleBuildModel = getFakeGradleBuildModel()
    val updatedPsi = GradleDependencyManager().addCircuitImport(gradleBuildModel, project)
    TestCase.assertTrue("circuit()" in updatedPsi!!.text)
  }
}
