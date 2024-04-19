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
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtScriptInitializer

class GradleDependencyManager {
  fun addMissingGradleDependency(project: Project, directory: String) {
    val repoRoot = project.basePath ?: return
    val gradlePath =
      findNearestProjectDirRecursive(Paths.get(repoRoot), Paths.get(directory), mutableMapOf())
        ?: return
    val gradleFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(gradlePath)
    val fileContent = gradlePath.toFile().readText()
    val gradleBuildModel =
      gradleFile?.let { ProjectBuildModel.get(project).getModuleBuildModel(it) }
    if (!fileContent.contains(parcelizeImport)) {
      addParcelizeImport(gradleBuildModel, project)
    }
    if (!fileContent.contains(circuitImport)) {
      addCircuitImport(gradleBuildModel, project)
    }
    gradleBuildModel?.psiFile?.subtreeChanged()
    gradleFile?.refresh(true, false)
  }

  fun addParcelizeImport(gradleBuildModel: GradleBuildModel?, project: Project): PsiElement? {
    val pluginBlocks = gradleBuildModel?.pluginsPsiElement

    pluginBlocks?.let { block ->
      WriteCommandAction.runWriteCommandAction(project) {
        val psiFactory = KtPsiFactory(project)
        block.add(psiFactory.createNewLine())
        block.add(psiFactory.createExpression(parcelizeImport))
        CodeStyleManager.getInstance(project).reformat(block)
      }
    }
    return pluginBlocks
  }

  fun addCircuitImport(gradleBuildModel: GradleBuildModel?, project: Project): PsiElement? {
    val scripts =
      PsiTreeUtil.findChildrenOfType(gradleBuildModel?.psiFile, KtScriptInitializer::class.java)
    val psiFactory = KtPsiFactory(project)

    scripts.forEach { script ->
      // Looking for a "slack" block
      val slackBlock = findBlock(script, "slack")
      slackBlock?.let {
        // Inside "slack", now look for "features" block
        val featuresBlock = findBlock(slackBlock, "features")
        if (featuresBlock == null) {
          val expression = "features { $circuitImport }"
          return addExpressionToBlock(slackBlock, expression, psiFactory, project)
        } else {
          return addExpressionToBlock(featuresBlock, circuitImport, psiFactory, project)
        }
      }
    }
    // Slack block does not exist, create slack and features with circuit
    val slackBlock =
      """
          slack {
              features { $circuitImport }
          }
        """
        .trimIndent()
    gradleBuildModel?.pluginsPsiElement?.let {
      return addExpressionToBlock(it, slackBlock, psiFactory, project)
    }
    return null
  }

  private fun findBlock(script: PsiElement, blockName: String): KtBlockExpression? =
    PsiTreeUtil.findChildrenOfType(script, KtCallExpression::class.java)
      .firstOrNull { it.calleeExpression?.textMatches(blockName) == true }
      ?.lambdaArguments
      ?.firstOrNull()
      ?.getLambdaExpression()
      ?.bodyExpression

  private fun addExpressionToBlock(
    block: PsiElement,
    expressionText: String,
    psiFactory: KtPsiFactory,
    project: Project,
  ): PsiElement {
    WriteCommandAction.runWriteCommandAction(project) {
      block.add(psiFactory.createNewLine())
      block.add(psiFactory.createExpression(expressionText))
      CodeStyleManager.getInstance(project).reformat(block)
    }
    return block
  }

  private fun findNearestProjectDirRecursive(
    repoRoot: Path,
    currentDir: Path?,
    cache: MutableMap<Path, Path?>,
  ): Path? {
    if (currentDir == null || currentDir == repoRoot) {
      return null
    }
    return cache.getOrPut(currentDir) {
      if (currentDir.resolve("build.gradle.kts").exists()) {
        return currentDir.resolve("build.gradle.kts")
      }
      findNearestProjectDirRecursive(repoRoot, currentDir.parent, cache)
    }
  }

  companion object {
    internal const val parcelizeImport = "alias(libs.plugins.kotlin.plugin.parcelize)"
    internal const val circuitImport = "circuit()"
  }
}
