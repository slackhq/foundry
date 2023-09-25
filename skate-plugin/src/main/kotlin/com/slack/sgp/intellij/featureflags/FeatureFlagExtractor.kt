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

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Responsible for extracting feature flags. Searches for enum entries annotated with 'FeatureFlag'
 * to identify feature flags.
 */
class FeatureFlagExtractor {

  private val log: Logger = Logger.getInstance(FeatureFlagExtractor::class.java)

  // Caches the feature flags for a given PSI file to optimize repeated lookups
  companion object {
    private val featureFlagCache = mutableMapOf<PsiFile, List<PsiElement>>()
  }

  fun setFeatureFlagsForPsiFile(psiFile: PsiFile, flags: List<PsiElement>) {
    featureFlagCache[psiFile] = flags
  }

  fun getFeatureFlagsForPsiFile(psiFile: PsiFile): List<PsiElement>? {
    return featureFlagCache[psiFile]
  }
  /**
   * Extracts the names of feature flags from the provided PSI file. Only processes Kotlin files.
   *
   * @param psiFile The PSI representation of the file to process.
   * @return A list of feature flag names in a file
   */
  fun extractFeatureFlags(psiFile: PsiFile): List<PsiElement> {
    log.info("Looking for feature flags in a file: $psiFile")
    val enumsWithAnnotation = findAnnotatedEnumElements(psiFile)
    log.info("Found feature flags: $enumsWithAnnotation")
    return enumsWithAnnotation
  }

  private fun findAnnotatedEnumElements(psiFile: PsiFile): List<PsiElement> {
    val annotatedEnumEntries = mutableListOf<PsiElement>()
    fun recurse(element: PsiElement) {
      if (element.isEnum() && element.hasAnnotation("FeatureFlag")) {
        element.getEnumIdentifier()?.let { annotatedEnumEntries.add(it) }
      }
      element.children.forEach { recurse(it) }
    }
    recurse(psiFile)
    return annotatedEnumEntries
  }
}
