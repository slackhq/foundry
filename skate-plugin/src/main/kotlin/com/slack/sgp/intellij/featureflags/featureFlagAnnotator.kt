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
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IconUtil
import com.slack.sgp.intellij.SkatePluginSettings
import com.slack.sgp.intellij.TEST_KOTLIN_LANGUAGE_ID_KEY
import com.slack.sgp.intellij.util.isLinkifiedFeatureFlagsEnabled
import java.net.URI
import javax.swing.Icon
import org.jetbrains.kotlin.idea.KotlinLanguage

class FeatureFlagAnnotator : ExternalAnnotator<PsiFile, List<FeatureFlagSymbol>>() {
  private val LOG: Logger = Logger.getInstance(FeatureFlagAnnotator::class.java)
  private val extractor = FeatureFlagExtractor()

  override fun collectInformation(file: PsiFile): PsiFile? {
    if (!isKotlinFeatureFile(file)) {
      LOG.info("Skipping non-Feature Kotlin file: $file")
      return null
    }
    LOG.info("Getting CollectedInformation for file: $file")
    if (extractor.getFeatureFlagsForPsiFile(file) == null) {
      LOG.info("Extracting featureFlags")
      val flags = extractor.extractFeatureFlags(file)
      LOG.info("FeatureFlags found: $flags")
      extractor.setFeatureFlagsForPsiFile(file, flags)
    }
    return file
  }

  override fun doAnnotate(collectedInfo: PsiFile): List<FeatureFlagSymbol> {
    LOG.info("Starting Annotation")
    if (!collectedInfo.project.isLinkifiedFeatureFlagsEnabled() || !isKotlinFile(collectedInfo)) {
      LOG.info("This is not a kotline file or FF not enabled for file: $collectedInfo")
      return emptyList()
    }

    val flagsForFile = extractor.getFeatureFlagsForPsiFile(collectedInfo)
    LOG.info("Transform Feature Flag method called")
    return transformToFeatureFlagSymbols(collectedInfo, flagsForFile)
  }

  private fun transformToFeatureFlagSymbols(
    psiFile: PsiFile,
    flags: List<String>?
  ): List<FeatureFlagSymbol> {
    LOG.info("Transforming feature flags: $flags")
    if (flags == null || flags.isEmpty()) {
      LOG.info("No flags to transform. Flags, returning empty list")
      return emptyList()
    }

    val settings = psiFile.project.service<SkatePluginSettings>()
    val baseUrl = settings.featureFlagBaseUrl.orEmpty()
    LOG.info("BaseURL is : $baseUrl")
    return flags.mapNotNull { flag ->
      val textRange =
        ApplicationManager.getApplication().runReadAction<TextRange?> {
          findTextRangeForFlag(psiFile, flag)
        }
      if (textRange != null) {
        val url = "$baseUrl?q=$flag"
        FeatureFlagSymbol(textRange, url)
      } else {
        null
      }
    }
  }

  private fun findTextRangeForFlag(psiFile: PsiFile, flag: String): TextRange? {
    LOG.info("Finding text range for a flag: $flag")
    // Use Pita's approach of traversing the PSI structure to find the text range
    val elements = PsiTreeUtil.findChildrenOfType(psiFile, PsiElement::class.java)
    for (element in elements) {
      if (element.text == flag) {
        LOG.info("Text range found: ${element.textRange}")
        return element.textRange
      }
    }
    return null
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
      LOG.info("Applying Annotation for a symbol: $symbol")
      val textRange = symbol.textRange
      val message = "Open on Houston: ${symbol.url}"
      holder
        .newAnnotation(
          HighlightSeverity.INFORMATION,
          "More details about feature flag on Houston..."
        )
        .range(textRange)
        .needsUpdateOnTyping(true)
        .withFix(UrlIntentionAction(message, symbol.url))
        .gutterIconRenderer(MyGutterIconRenderer(symbol.url))
        .create()
    }
  }
}

class FeatureFlagSymbol(val textRange: TextRange, val url: String)

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

class MyGutterIconRenderer(private val url: String) : GutterIconRenderer() {

  private val scaledWebIcon: Icon = IconUtil.scale(AllIcons.General.Web, null, 0.5f)

  override fun getIcon() = scaledWebIcon

  override fun getClickAction() =
    object : AnAction() {
      override fun actionPerformed(e: AnActionEvent) {
        BrowserUtil.browse(url)
      }
    }

  override fun equals(other: Any?) = other is MyGutterIconRenderer && other.url == url

  override fun hashCode() = url.hashCode()
}
