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

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.ide.BrowserUtil
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.slack.sgp.intellij.SkatePluginSettings
import com.slack.sgp.intellij.TEST_KOTLIN_LANGUAGE_ID_KEY
import com.slack.sgp.intellij.util.isLinkifiedFeatureFlagsEnabled
import java.net.URI
import org.jetbrains.kotlin.idea.KotlinLanguage

class FeatureFlagAnnotator : ExternalAnnotator<PsiFile, List<FeatureFlagSymbol>>() {
  private val extractor by lazy { FeatureFlagExtractor() }

  override fun collectInformation(file: PsiFile): PsiFile? {
    if (!isKotlinFeatureFile(file)) {
      return null
    }
    if (extractor.getFeatureFlagsForPsiFile(file) == null) {
      val flags = extractor.extractFeatureFlags(file)
      extractor.setFeatureFlagsForPsiFile(file, flags)
    }
    return file
  }

  override fun doAnnotate(collectedInfo: PsiFile): List<FeatureFlagSymbol> {
    if (!collectedInfo.project.isLinkifiedFeatureFlagsEnabled() || !isKotlinFile(collectedInfo)) {
      return emptyList()
    }

    val flagsForFile = extractor.getFeatureFlagsForPsiFile(collectedInfo)
    return transformToFeatureFlagSymbols(collectedInfo, flagsForFile)
  }

  private fun transformToFeatureFlagSymbols(
    psiFile: PsiFile,
    flags: List<PsiElement>?
  ): List<FeatureFlagSymbol> {
    if (flags.isNullOrEmpty()) {
      return emptyList()
    }
    val baseUrl = psiFile.project.service<SkatePluginSettings>().featureFlagBaseUrl.orEmpty()

    return flags.map { flag -> FeatureFlagSymbol(flag, "$baseUrl?q=${flag.text}") }
  }

  private fun isKotlinFile(psiFile: PsiFile): Boolean =
    psiFile.language.id == KotlinLanguage.INSTANCE.id ||
      psiFile.getUserData(TEST_KOTLIN_LANGUAGE_ID_KEY) == KotlinLanguage.INSTANCE.id

  private fun isKotlinFeatureFile(psiFile: PsiFile): Boolean = psiFile.name.endsWith("Feature.kt")

  override fun apply(
    file: PsiFile,
    annotationResult: List<FeatureFlagSymbol>,
    holder: AnnotationHolder
  ) {
    for (symbol in annotationResult) {
      val message = "Open on Houston: ${symbol.url}"
      holder
        .newAnnotation(
          HighlightSeverity.INFORMATION,
          "More details about feature flag on Houston..."
        )
        .range(symbol.element)
        .needsUpdateOnTyping(true)
        .withFix(UrlIntentionAction(message, symbol.url))
        .create()
    }
  }
}

class FeatureFlagSymbol(val element: PsiElement, val url: String)

class UrlIntentionAction(
  private val message: String,
  private val url: String,
) : IntentionAction {
  override fun getText(): String = message

  override fun getFamilyName(): String = text

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    BrowserUtil.browse(URI(url))
  }

  override fun startInWriteAction(): Boolean {
    return false
  }
}
