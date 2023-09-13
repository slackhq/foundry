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
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.IconUtil
import javax.swing.Icon

class FeatureFlagAnnotator : Annotator {
  private val LOG: Logger = Logger.getInstance(FeatureFlagAnnotator::class.java)
  private val extractor = FeatureFlagExtractor()

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {

    val psiFile = element.containingFile
    LOG.info("Let's look at adding annotation for $element")
    // Fetch feature flags for the current file from the cache
    val flagsForFile = extractor.getFeatureFlagsForPsiFile(psiFile)
    LOG.info("Let's add annotation to flags in a file : ${flagsForFile.toString()}")

    // If this element's name is in the cache, annotate it
    if (extractor.isKtEnumEntry(element) && extractor.hasFeatureFlagAnnotation(element)) {
      LOG.info("Check featureflag is in a cache")
      val featureFlagName = extractor.getElementName(element) ?: return
      val url = "https://houston.tinyspeck.com/experiments/all?q=$featureFlagName"
      LOG.info("Creating URL: $url")
      val range = element.textRange
      holder
        .newAnnotation(HighlightSeverity.INFORMATION, "More details about $featureFlagName")
        .range(range)
        .textAttributes(TextAttributesKey.createTextAttributesKey("DEFAULT_HYPERLINK"))
        .needsUpdateOnTyping(true)
        .withFix(OpenUrlIntentionAction(url))
        .gutterIconRenderer(MyGutterIconRenderer(url))
        .create()
    }
  }
}

class OpenUrlIntentionAction(private val url: String) : IntentionAction {
  override fun getText() = "Open feature flag details in browser"

  override fun getFamilyName() = "Open URL"

  override fun isAvailable(project: Project, editor: Editor?, psiFile: PsiFile?) = true

  override fun invoke(project: Project, editor: Editor?, psiFile: PsiFile?) {
    BrowserUtil.browse(url)
  }

  override fun startInWriteAction(): Boolean {
    return false
  }
}

class MyGutterIconRenderer(private val url: String) : GutterIconRenderer() {

  // Define a scaled icon based on the original
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
