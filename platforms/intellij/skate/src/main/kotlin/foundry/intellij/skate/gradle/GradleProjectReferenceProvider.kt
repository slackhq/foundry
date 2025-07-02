/*
 * Copyright (C) 2025 Slack Technologies, LLC
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
package foundry.intellij.skate.gradle

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/** Contributes references for project paths in project(...) calls, making them clickable. */
// TODO this works during Gradle sync but not after sync failures
class GradleProjectReferenceContributor : PsiReferenceContributor() {

  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    val gradleFilePattern =
      PlatformPatterns.psiFile()
        .withName(
          PlatformPatterns.string()
            .with(
              object : PatternCondition<String>("gradle build file") {
                override fun accepts(t: String, context: ProcessingContext?): Boolean {
                  return t.endsWith(".gradle") || t.endsWith(".gradle.kts")
                }
              }
            )
        )

    // For Kotlin Gradle files - target string template expressions
    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(KtStringTemplateExpression::class.java).inFile(gradleFilePattern),
      GradleProjectReferenceProvider(),
    )

    // For Groovy Gradle files - target literal expressions
    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(PsiLiteralExpression::class.java).inFile(gradleFilePattern),
      GradleProjectReferenceProvider(),
    )

    // Fallback pattern for quoted strings
    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement()
        .withText(StandardPatterns.string().matches("\":[^\"]+\""))
        .inFile(gradleFilePattern),
      GradleProjectReferenceProvider(),
    )
  }
}

class GradleProjectReferenceProvider : PsiReferenceProvider() {
  override fun getReferencesByElement(
    element: PsiElement,
    context: ProcessingContext,
  ): Array<PsiReference> {
    // Handle different PSI element types
    val elementText =
      when (element) {
        is KtStringTemplateExpression -> {
          // For Kotlin string templates, get the content
          element.entries.joinToString("") { it.text }
        }
        is PsiLiteralExpression -> {
          // For Java/Groovy literals, get the value
          element.value?.toString() ?: element.text
        }
        else -> element.text
      }

    // Look for string literals that contain project paths
    val projectPath =
      when {
        elementText.startsWith("\":") && elementText.endsWith("\"") -> {
          elementText.substring(1, elementText.length - 1) // Remove double quotes
        }
        elementText.startsWith("':") && elementText.endsWith("'") -> {
          elementText.substring(1, elementText.length - 1) // Remove single quotes
        }
        elementText.startsWith(":") &&
          !elementText.contains("\"") &&
          !elementText.contains("'") -> {
          elementText // Raw project path
        }
        else -> null
      }

    if (projectPath != null && projectPath.startsWith(":")) {
      // Verify this is in a project() call context by checking ancestors
      if (isInProjectCall(element)) {
        // Check if it's a valid project path
        val projectPathService = element.project.getService(ProjectPathService::class.java)
        if (projectPathService.isValidProjectPath(projectPath)) {
          // Calculate range based on element type
          val range = calculateReferenceTextRange(element.text)
          return arrayOf(GradleProjectReference(element, range, projectPath))
        }
      }
    }

    return PsiReference.EMPTY_ARRAY
  }

  private fun isInProjectCall(element: PsiElement): Boolean {
    // Walk up the PSI tree to find if we're inside a project() call
    var current: PsiElement? = element
    var depth = 0
    while (current != null && depth < 10) {
      val currentText = current.text
      if (currentText.contains("project(") && currentText.contains(element.text)) {
        return true
      }
      current = current.parent
      depth++
    }
    return false
  }
}

/** Internal function to calculate text range for references. Exposed for testing. */
internal fun calculateReferenceTextRange(elementText: String): TextRange {
  return when {
    elementText.startsWith("\"") || elementText.startsWith("'") -> {
      TextRange.create(1, elementText.length - 1) // Exclude quotes
    }
    else -> {
      TextRange.create(0, elementText.length) // Entire element
    }
  }
}
