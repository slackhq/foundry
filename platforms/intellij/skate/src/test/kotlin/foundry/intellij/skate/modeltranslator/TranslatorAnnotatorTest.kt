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

import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import foundry.intellij.skate.DEFAULT_TRANSLATOR_FILE_NAME_SUFFIX
import foundry.intellij.skate.DEFAULT_TRANSLATOR_SOURCE_MODELS_PACKAGE_NAME
import foundry.intellij.skate.SkateBundle
import foundry.intellij.skate.util.settings
import org.junit.Ignore

@Ignore("TranslatorAnnotator doesn't appear to work in 241+")
class TranslatorAnnotatorTest : LightJavaCodeInsightFixtureTestCase() {

  private val warningDescription = SkateBundle.message("skate.modelTranslator.description")

  override fun getTestDataPath(): String {
    return "src/test/testData"
  }

  override fun setUp() {
    super.setUp()

    // Reset relevant settings.
    val settings = project.settings()
    settings.translatorSourceModelsPackageName = DEFAULT_TRANSLATOR_SOURCE_MODELS_PACKAGE_NAME
    settings.translatorFileNameSuffix = DEFAULT_TRANSLATOR_FILE_NAME_SUFFIX
  }

  fun testAnnotator() {
    myFixture.configureByFiles("CallTranslator.kt", "Call.kt")

    val translatorWarning =
      myFixture.doHighlighting().firstOrNull { it.severity == HighlightSeverity.WEAK_WARNING }
    assertTranslatorWarning(translatorWarning)
  }

  fun testAnnotator_StringSource_EnumDestination() {
    myFixture.configureByFiles("StatusStringTranslator.kt", "Call.kt")

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

  fun testAnnotator_AlternativeFileNameSuffix() {
    val settings = project.settings()
    settings.translatorFileNameSuffix = "Extensions.kt"

    myFixture.configureByFiles("CallExtensions.kt", "Call.kt")

    val translatorWarning =
      myFixture.doHighlighting().firstOrNull { it.severity == HighlightSeverity.WEAK_WARNING }
    assertThat(translatorWarning).isNotNull()
    assertTranslatorWarning(translatorWarning)
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

  fun testAnnotator_AlternativeSourcePackageName() {
    val settings = project.settings()
    settings.translatorSourceModelsPackageName = "slack.api"

    myFixture.configureByFiles("CallObjectsTranslator.kt", "Call.kt")

    val translatorWarning =
      myFixture.doHighlighting().firstOrNull { it.severity == HighlightSeverity.WEAK_WARNING }
    assertThat(translatorWarning).isNotNull()
    assertTranslatorWarning(translatorWarning)
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

  fun testAnnotator_StringSource_NonEnumDestination() {
    myFixture.configureByFiles("TranscriptionStringTranslator.kt", "Call.kt")

    val translatorWarning =
      myFixture.doHighlighting().firstOrNull { it.severity == HighlightSeverity.WEAK_WARNING }
    assertThat(translatorWarning).isNull()
  }

  private fun assertTranslatorWarning(translatorWarning: HighlightInfo?) {
    assertThat(translatorWarning).isNotNull()
    assertThat(translatorWarning!!.description).isEqualTo(warningDescription)
  }
}
