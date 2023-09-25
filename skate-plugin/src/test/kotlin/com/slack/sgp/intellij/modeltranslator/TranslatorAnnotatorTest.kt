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
package com.slack.sgp.intellij.modeltranslator

import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.slack.sgp.intellij.SkateBundle

class TranslatorAnnotatorTest : LightJavaCodeInsightFixtureTestCase() {

  private val warningDescription = SkateBundle.message("skate.modelTranslator.description")

  override fun getTestDataPath(): String {
    return "src/test/testData"
  }

  fun testAnnotator() {
    myFixture.configureByFiles("CallTranslator.kt", "Call.kt")

    val translatorWarning =
      myFixture.doHighlighting().firstOrNull { it.severity == HighlightSeverity.WEAK_WARNING }
    assertTranslatorWarning(translatorWarning)
  }

  fun testAnnotator_NestedSource() {
    myFixture.configureByFiles("ActionTranslator.kt", "Call.kt")

    val translatorWarning =
      myFixture.doHighlighting().firstOrNull { it.severity == HighlightSeverity.WEAK_WARNING }
    assertTranslatorWarning(translatorWarning)
  }

  fun testAnnotator_FqNameSource() {
    myFixture.configureByFiles("FullyQualifiedCallTranslator.kt", "Call.kt")

    val translatorWarning =
      myFixture.doHighlighting().firstOrNull { it.severity == HighlightSeverity.WEAK_WARNING }
    assertTranslatorWarning(translatorWarning)
  }

  fun testAnnotator_ImportAlias() {
    myFixture.configureByFiles("ImportAliasCallTranslator.kt", "Call.kt")

    val translatorWarning =
      myFixture.doHighlighting().firstOrNull { it.severity == HighlightSeverity.WEAK_WARNING }
    assertTranslatorWarning(translatorWarning)
  }

  fun testAnnotator_WrongFileName() {
    myFixture.configureByFiles("CallExtensions.kt", "Call.kt")

    val translatorWarning =
      myFixture.doHighlighting().firstOrNull { it.severity == HighlightSeverity.WEAK_WARNING }
    assertThat(translatorWarning).isNull()
  }

  fun testAnnotator_NoTranslator() {
    myFixture.configureByFiles("Call.kt")

    val translatorWarning =
      myFixture.doHighlighting().firstOrNull { it.severity == HighlightSeverity.WEAK_WARNING }
    assertThat(translatorWarning).isNull()
  }

  fun testAnnotator_SourceHasWrongPackageName() {
    myFixture.configureByFiles("CallObjectsTranslator.kt", "Call.kt")

    val translatorWarning =
      myFixture.doHighlighting().firstOrNull { it.severity == HighlightSeverity.WEAK_WARNING }
    assertThat(translatorWarning).isNull()
  }

  fun testAnnotator_NoDestination() {
    myFixture.configureByFiles("CallObjectTranslator.kt", "Call.kt")

    val translatorWarning =
      myFixture.doHighlighting().firstOrNull { it.severity == HighlightSeverity.WEAK_WARNING }
    assertThat(translatorWarning).isNull()
  }

  fun testAnnotator_NoBodyExpression() {
    myFixture.configureByFiles("SingleLineCallTranslator.kt", "Call.kt")

    val translatorWarning =
      myFixture.doHighlighting().firstOrNull { it.severity == HighlightSeverity.WEAK_WARNING }
    assertThat(translatorWarning).isNull()
  }

  fun testAnnotator_HasReturnExpression() {
    myFixture.configureByFiles("NullableCallTranslator.kt", "Call.kt")

    val translatorWarning =
      myFixture.doHighlighting().firstOrNull { it.severity == HighlightSeverity.WEAK_WARNING }
    assertThat(translatorWarning).isNull()
  }

  private fun assertTranslatorWarning(translatorWarning: HighlightInfo?) {
    assertThat(translatorWarning).isNotNull()
    assertThat(translatorWarning!!.description).isEqualTo(warningDescription)
  }
}
