package com.slack.sgp.intellij.circuitgenerator

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase
import java.nio.file.Path
import java.nio.file.Paths

class GradleDependencyManagementTest : BasePlatformTestCase() {


  fun testAddParcelizeImport() {
    val testGradlePath = "src/test/resources/test_build.gradle.kts"
    val gradlePath = Path.of(this.basePath, testGradlePath)
    val gradleFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(gradlePath)
    val gradleBuildModel = gradleFile?.let { ProjectBuildModel.get(project).getModuleBuildModel(it) }

    val pluginBlocks = GradleDependencyManager().addParcelizeImport(gradleBuildModel, project)
    TestCase.assertTrue("alias(libs.plugins.kotlin.plugin.parcelize)" in pluginBlocks!!.text)
  }

  fun testAddCircuitImport() {
    val testGradlePath = "src/test/resources/test_build.gradle.kts"
    val gradlePath = Path.of(this.basePath, testGradlePath)
    val gradleFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(gradlePath)

    val gradleBuildModel = gradleFile?.let { ProjectBuildModel.get(project).getModuleBuildModel(it) }

    val updatedPsi = GradleDependencyManager().addCircuitImport(gradleBuildModel, project)
    TestCase.assertTrue("circuit()" in updatedPsi!!.text)
  }
}
