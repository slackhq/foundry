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
package foundry.intellij.skate.modeltranslator

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import foundry.intellij.skate.SkateBundle
import foundry.intellij.skate.modeltranslator.helper.TranslatorHelper
import foundry.intellij.skate.modeltranslator.model.TranslatorBundle
import foundry.intellij.skate.tracing.SkateSpanBuilder
import foundry.intellij.skate.tracing.SkateTracingEvent
import foundry.intellij.skate.util.getTraceReporter
import foundry.intellij.skate.util.isTracingEnabled
import java.time.Instant

class GenerateTranslatorBodyAction(private val bundle: TranslatorBundle) : IntentionAction {
  override fun getText() = SkateBundle.message("skate.modelTranslator.description")

  override fun getFamilyName() = text

  override fun isAvailable(project: Project, editor: Editor?, psiFile: PsiFile?) = true

  override fun startInWriteAction() = true

  override fun invoke(project: Project, editor: Editor?, psiFile: PsiFile?) {
    val startTimestamp = Instant.now()
    val body = TranslatorHelper.generateBody(bundle)
    if (body != null) {
      bundle.element.bodyBlockExpression?.replace(body) ?: LOG.warn("Body block expression is null")
      if (project.isTracingEnabled()) {
        sendUsageTrace(project, startTimestamp)
      }
    } else {
      LOG.warn("Generated body is null")
    }
  }

  private fun sendUsageTrace(project: Project, startTimestamp: Instant) {
    val skateSpanBuilder =
      SkateSpanBuilder().apply {
        addTag("event", SkateTracingEvent.ModelTranslator.MODEL_TRANSLATOR_GENERATED)
      }
    project
      .getTraceReporter()
      .createPluginUsageTraceAndSendTrace(
        "model_translator",
        startTimestamp,
        skateSpanBuilder.getKeyValueList(),
      )
  }

  companion object {
    private val LOG: Logger = Logger.getInstance(GenerateTranslatorBodyAction::class.java)
  }
}
