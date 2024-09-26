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

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import foundry.intellij.skate.SkateBundle
import foundry.intellij.skate.modeltranslator.helper.TranslatorHelper

class TranslatorAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    val bundle = TranslatorHelper.extractBundle(element)

    if (bundle != null)
      holder
        .newAnnotation(
          HighlightSeverity.WEAK_WARNING,
          SkateBundle.message("skate.modelTranslator.description"),
        )
        .range(bundle.functionHeaderRange)
        .needsUpdateOnTyping(true)
        .withFix(GenerateTranslatorBodyAction(bundle))
        .create()
  }
}
