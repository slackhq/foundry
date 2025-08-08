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

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import foundry.intellij.skate.SkatePluginSettings
import java.util.regex.Pattern

internal val PROJECT_CALL_PATTERN = Pattern.compile("""project\s*\(\s*["']([^"']+)["']\s*\)""")

/**
 * Annotator that detects invalid project paths in project(...) calls and highlights them as errors.
 */
class GradleProjectAnnotator : Annotator {

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    // Check if the ProjectPathService is enabled
    val settings = element.project.getService(SkatePluginSettings::class.java)
    if (!settings.isProjectPathServiceEnabled) {
      return
    }

    // Only process elements in Gradle build files
    val file = element.containingFile
    if (file?.name?.endsWith(".gradle") != true && file?.name?.endsWith(".gradle.kts") != true) {
      return
    }

    val elementText = element.text

    // Only process elements that contain project( AND don't have children containing project(
    // This prevents processing both parent and child elements with the same content
    if (!elementText.contains("project(")) {
      return
    }

    // Skip if any child element also contains "project(" - let the child handle it
    if (element.children.any { it.text.contains("project(") }) {
      return
    }

    val projectPathService = element.project.getService(ProjectPathService::class.java)

    // Find all project(...) calls in the current element
    val matcher = PROJECT_CALL_PATTERN.matcher(elementText)

    while (matcher.find()) {
      val projectPath = matcher.group(1)
      val startOffset = element.textRange.startOffset + matcher.start(1)
      val endOffset = element.textRange.startOffset + matcher.end(1)
      val range = TextRange.create(startOffset, endOffset)

      if (!projectPathService.isValidProjectPath(projectPath)) {
        // Create error annotation for invalid project path
        holder
          .newAnnotation(HighlightSeverity.ERROR, "Unknown project path: '$projectPath'")
          .range(range)
          .withFix(RefreshProjectPathsIntentionAction())
          .create()
      }
    }
  }
}

/**
 * Internal function to check if an element should be processed by the annotator. Exposed for
 * testing.
 */
internal fun shouldAnnotatorProcessElement(
  elementText: String,
  hasProjectChildren: Boolean,
): Boolean {
  if (!elementText.contains("project(")) {
    return false
  }

  // Skip if any child element also contains "project(" - let the child handle it
  if (hasProjectChildren) {
    return false
  }

  return true
}
