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
import com.intellij.psi.util.childrenOfType

fun PsiElement.getFirstChild(elementType: String): PsiElement? {
  return this.childrenOfType<PsiElement>().firstOrNull {
    it.node.elementType.toString() == elementType
  }
}

fun PsiElement.getChildren(elementType: String): List<PsiElement> {
  return this.childrenOfType<PsiElement>().filter { it.node.elementType.toString() == elementType }
}

fun PsiElement.getAnnotationEntries(): List<PsiElement> {
  return this.getFirstChild("MODIFIER_LIST")?.getChildren("ANNOTATION_ENTRY") ?: listOf()
}

fun PsiElement.getAnnotationName(): String? {
  return this.getFirstChild("CONSTRUCTOR_CALLEE")?.text
}

fun PsiElement.isEnum(): Boolean {
  return this.node?.elementType?.toString() == "ENUM_ENTRY"
}

fun PsiElement.hasAnnotation(name: String): Boolean {
  return this.getAnnotationEntries().any { it.getAnnotationName() == name }
}

fun PsiElement.getEnumIdentifier(): PsiElement? {
  return this.getFirstChild("IDENTIFIER")
}
