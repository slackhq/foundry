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
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.slack.sgp.intellij.tracing.SkateSpanBuilder
import com.slack.sgp.intellij.tracing.SkateTraceReporter
import com.slack.sgp.intellij.tracing.SkateTracingEvent
import com.slack.sgp.intellij.tracing.SkateTracingEvent.EventType.HOUSTON_FEATURE_FLAG_URL_CLICKED
import com.slack.sgp.intellij.util.featureFlagFilePattern
import com.slack.sgp.intellij.util.isLinkifiedFeatureFlagsEnabled
import com.slack.sgp.intellij.util.isTracingEnabled
import java.net.URI
import java.time.Instant
import org.jetbrains.kotlin.psi.KtFile

class FeatureFlagAnnotator : ExternalAnnotator<List<FeatureFlagSymbol>, List<FeatureFlagSymbol>>() {
  override fun collectInformation(file: PsiFile): List<FeatureFlagSymbol> {
    val isEligibleForLinkifiedFeatureProcessing =
      file.project.isLinkifiedFeatureFlagsEnabled() && file is KtFile && isKotlinFeatureFile(file)

    if (!isEligibleForLinkifiedFeatureProcessing) {
      return emptyList()
    }
    return FeatureFlagExtractor.extractFeatureFlags(file)
  }

  override fun doAnnotate(collectedInfo: List<FeatureFlagSymbol>): List<FeatureFlagSymbol> =
    collectedInfo

  override fun apply(
    file: PsiFile,
    annotationResult: List<FeatureFlagSymbol>,
    holder: AnnotationHolder
  ) {
    for (symbol in annotationResult) {
      val message = "Open at: ${symbol.url}"
      holder
        .newAnnotation(HighlightSeverity.INFORMATION, "Open for more details.")
        .range(symbol.range)
        .needsUpdateOnTyping(true)
        .withFix(UrlIntentionAction(message, symbol.url))
        .create()
    }
  }

  private fun isKotlinFeatureFile(psiFile: PsiFile): Boolean {
    val filePattern = psiFile.project.featureFlagFilePattern() ?: return false
    return filePattern.toRegex().matches(psiFile.name)
  }
}

class UrlIntentionAction(
  private val message: String,
  private val url: String,
) : IntentionAction {

  private val startTimestamp = Instant.now()
  private val skateSpanBuilder = SkateSpanBuilder()

  override fun getText(): String = message

  override fun getFamilyName(): String = text

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    BrowserUtil.browse(URI(url))
    skateSpanBuilder.addSpanTag("event", SkateTracingEvent(HOUSTON_FEATURE_FLAG_URL_CLICKED))
    sendUsageTrace(project, project.isTracingEnabled())
  }

  override fun startInWriteAction(): Boolean {
    return false
  }

  fun sendUsageTrace(project: Project, isTracingEnabled: Boolean) {
    if (!isTracingEnabled) return
    SkateTraceReporter(project)
      .createPluginUsageTraceAndSendTrace(
        "feature_flag_annotator",
        startTimestamp,
        skateSpanBuilder.getKeyValueList()
      )
  }
}
