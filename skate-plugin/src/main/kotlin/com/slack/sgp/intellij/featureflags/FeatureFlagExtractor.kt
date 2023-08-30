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
package com.slack.sgp.intellij.featureflags

import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Extracts feature flags from Kotlin files. It searches for enum entries annotated with
 * 'FeatureFlag'.
 */
class FeatureFlagExtractor {

  /**
   * Extracts the names of feature flags from the provided PSI file. Only processes Kotlin files.
   *
   * @param psiFile The PSI representation of the file to process.
   * @return A list of feature flag names in a file
   */
  fun extractFeatureFlags(psiFile: com.intellij.psi.PsiFile): List<String> {
    if (psiFile !is KtFile) return emptyList()

    // Fetch all the enum entries in the file
    val enumEntries = psiFile.collectDescendantsOfType<KtEnumEntry>()

    // Filter the enums that are feature flags and extract their names
    return enumEntries.filter { isFeatureFlagEnum(it) }.mapNotNull { it.name }
  }

  /**
   * Checks if a given enum entry is a feature flag. An enum is considered a feature flag if it's
   * within a Kotlin class and has an annotation named "FeatureFlag".
   *
   * @param element The enum entry to check.
   * @return true if the enum entry is a feature flag, false otherwise.
   */
  private fun isFeatureFlagEnum(element: KtEnumEntry): Boolean {
    val parentClass = element.parent?.parent
    if (parentClass is KtClass) {
      val hasFeatureFlagAnnotation =
        element.annotationEntries.any { it.shortName?.asString() == "FeatureFlag" }
      if (hasFeatureFlagAnnotation) {
        return true
      }
    }
    return false
  }
}
