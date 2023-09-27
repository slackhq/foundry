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

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.childrenOfType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.toUElement

fun KtEnumEntry.getAnnotation(name: String): KtAnnotationEntry? {
  return this.getAnnotationEntries().firstOrNull { it.shortName?.asString() == name }
}

fun PsiElement.getEnumIdentifier(): LeafPsiElement? {
  return this.childrenOfType<LeafPsiElement>().firstOrNull { it.elementType == KtTokens.IDENTIFIER }
}

fun KtAnnotationEntry.getKeyArgumentValue(key: String): String? {
  val argumentExpression =
    this.valueArguments
      .firstOrNull { it.getArgumentName()?.asName?.asString() == key }
      ?.getArgumentExpression()
  return (argumentExpression as? PsiElement)?.toUElement(UExpression::class.java)?.evaluate()
    as? String
}
