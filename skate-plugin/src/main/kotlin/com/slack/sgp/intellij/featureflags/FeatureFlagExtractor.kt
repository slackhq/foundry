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
 * Extracts feature flags from Kotlin files. It searches for enum entries annotated with
 * 'FeatureFlag'.
 */
class FeatureFlagExtractor {

  private val LOG: Logger = Logger.getInstance(FeatureFlagExtractor::class.java)

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
    if (!isKtFile(psiFile)) {
      LOG.info("$psiFile is not a KtFile")
      return emptyList()
    }
    LOG.info("Getting Enum Entries")
    val enumsWithAnnotation = findAnnotatedEnums(psiFile)
    LOG.info("Enums with Annotations: $enumsWithAnnotation")
    return enumsWithAnnotation
  }

  fun isKtFile(obj: Any): Boolean {
    return obj.javaClass.getName() == "org.jetbrains.kotlin.psi.KtFile"
  }

  fun findAnnotatedEnums(psiFile: Any): List<String> {
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
      // Traverse children
      element.javaClass.methods
        .find { it.name == "getChildren" }
        ?.let { method -> (method.invoke(element) as? Array<*>)?.forEach { recurse(it!!) } }
    }
    recurse(psiFile)
    return annotatedEnumEntries
  }

  /**
   * Checks if a given enum entry is a feature flag. An enum is considered a feature flag if it's
   * within a Kotlin class and has an annotation named "FeatureFlag".
   *
   * @param element The enum entry to check.
   * @return true if the enum entry is a feature flag, false otherwise.
   */
  fun hasFeatureFlagAnnotation(element: Any): Boolean {
    val annotationEntriesMethod = element.javaClass.getMethod("getAnnotationEntries")
    val annotationEntries = annotationEntriesMethod.invoke(element) as? List<*>
    return annotationEntries?.any {
      val shortNameMethod = it!!.javaClass.getMethod("getShortName")
      val shortName = shortNameMethod.invoke(it)
      shortName?.toString() == "FeatureFlag"
    }
      ?: false
  }

  fun isKtEnumEntry(element: Any): Boolean {
    LOG.info("Checking if element is KtEnumEntry")
    val result = element.javaClass.name == "org.jetbrains.kotlin.psi.KtEnumEntry"
    LOG.info("Element isKtEnumEntry: $result")
    return result
  }

  fun getElementName(element: Any): String? {
    return try {
      val nameMethod = element.javaClass.getMethod("getName")
      nameMethod.invoke(element) as? String
    } catch (e: NoSuchMethodException) {
      null
    }
  }
}
