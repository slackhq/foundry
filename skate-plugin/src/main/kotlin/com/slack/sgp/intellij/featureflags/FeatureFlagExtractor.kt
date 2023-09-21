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

/**
 * Responsible for extracting feature flags. Searches for enum entries annotated with 'FeatureFlag'
 * to identify feature flags.
 */
class FeatureFlagExtractor {

  private val log: Logger = Logger.getInstance(FeatureFlagExtractor::class.java)

  // Caches the feature flags for a given PSI file to optimize repeated lookups
  companion object {
    private val featureFlagCache = mutableMapOf<com.intellij.psi.PsiFile, List<String>>()
  }

  fun setFeatureFlagsForPsiFile(psiFile: com.intellij.psi.PsiFile, flags: List<String>) {
    featureFlagCache[psiFile] = flags
  }

  fun getFeatureFlagsForPsiFile(psiFile: com.intellij.psi.PsiFile): List<String>? {
    return featureFlagCache[psiFile]
  }
  /**
   * Extracts the names of feature flags from the provided PSI file. Only processes Kotlin files.
   *
   * @param psiFile The PSI representation of the file to process.
   * @return A list of feature flag names in a file
   */
  fun extractFeatureFlags(psiFile: com.intellij.psi.PsiFile): List<String> {
    log.info("Looking for feature flags in a file: ${psiFile.toString()}")
    val enumsWithAnnotation = findAnnotatedEnums(psiFile)
    log.info("Found feature flags: $enumsWithAnnotation")
    return enumsWithAnnotation
  }

  /** Recursively searches the given PSI file for enums with 'FeatureFlag' annotations. */
  private fun findAnnotatedEnums(psiFile: Any): List<String> {
    val annotatedEnumEntries = mutableListOf<String>()

    fun addIfAnnotatedEnum(element: Any) {
      if (isKtEnumEntry(element) && hasFeatureFlagAnnotation(element)) {
        element.javaClass
          .getMethod("getName")
          .invoke(element)
          .takeIf { it is String }
          ?.let { annotatedEnumEntries.add(it as String) }
      }
    }

    fun recurse(element: Any) {
      addIfAnnotatedEnum(element)
      element.javaClass.methods
        .find { it.name == "getChildren" }
        ?.let { method -> (method.invoke(element) as? Array<*>)?.forEach { recurse(it!!) } }
    }
    recurse(psiFile)
    return annotatedEnumEntries
  }

  /**
   * Determines if a given PSI element is an enum entry annotated with 'FeatureFlag'.
   *
   * @param element The enum entry to check.
   * @return true if the enum entry is a feature flag, false otherwise.
   */
  private fun hasFeatureFlagAnnotation(element: Any): Boolean {
    val annotationEntriesMethod = element.javaClass.getMethod("getAnnotationEntries")
    val annotationEntries = annotationEntriesMethod.invoke(element) as? List<*>
    return annotationEntries?.any {
      val shortNameMethod = it!!.javaClass.getMethod("getShortName")
      val shortName = shortNameMethod.invoke(it)
      shortName?.toString() == "FeatureFlag"
    } ?: false
  }

  /** Checks if the given PSI element is a Kotlin enum entry. */
  private fun isKtEnumEntry(element: Any): Boolean {
    log.info("Checking if element is a Kotlin Enum Entry")
    val result = element.javaClass.name == "org.jetbrains.kotlin.psi.KtEnumEntry"
    log.info("Element is Kotlin Enum Entry: $result")
    return result
  }
}
