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
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import foundry.intellij.skate.modeltranslator.helper.TranslatorHelper
import foundry.intellij.skate.util.settings
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.junit.Test

private const val ASSIGNMENTS =
  "id = id, dateStart = dateStart, dateEnded = dateEnd, activeParticipantCount = activeParticipantCount, title = title.toDomainModel(), customTitle = customTitle, outgoingToUser = outgoing.toDomainModel(), incomingFromUser = incoming.toDomainModel(), activeParticipants = activeParticipants, actions = actions.map { it.toDomainModel() }, retryText = retryText.toDomainModel()"

class TranslatorHelperTest : LightPlatformCodeInsightFixture4TestCase() {
  override fun getTestDataPath(): String {
    return "src/test/testData"
  }

  @Test
  fun testGenerateBody_String_Enum() {
    val settings = project.settings()
    settings.translatorEnumIdentifier = "getSerializedName()"
    myFixture.configureByFiles("StatusStringTranslator.kt", "Call.kt")

    val body = generateBody()

    assertThat(body).isNotNull()
    assertThat(body!!.text)
      .isEqualTo(
        """
        {
        return when(this) {
        Call.Transcription.Status.PROCESSING.getSerializedName() -> Call.Transcription.Status.PROCESSING
        Call.Transcription.Status.FAILED.getSerializedName() -> Call.Transcription.Status.FAILED
        Call.Transcription.Status.COMPLETE.getSerializedName() -> Call.Transcription.Status.COMPLETE
        else -> Call.Transcription.Status.UNKNOWN
        }
        }
        """
          .trimIndent()
      )
  }

  @Test
  fun testGenerateBody_Enum() {
    myFixture.configureByFiles("ActionTranslator.kt", "Call.kt")

    val body = generateBody()

    assertThat(body).isNotNull()
    assertThat(body!!.text)
      .isEqualTo(
        """
        {
        return when(this) {
        CallObject.Action.RETRY -> Call.Action.RETRY
        CallObject.Action.DECLINE -> Call.Action.DECLINE
        CallObject.Action.JOIN -> Call.Action.JOIN
        else -> Call.Action.UNKNOWN
        }
        }
        """
          .trimIndent()
      )
  }

  @Test
  fun testGenerateBody_Enum_FqNameDestination() {
    myFixture.configureByFiles("FullyQualifiedActionTranslator.kt", "Call.kt")

    val body = generateBody()

    assertThat(body).isNotNull()
    assertThat(body!!.text)
      .isEqualTo(
        """
        {
        return when(this) {
        slack.api.schemas.CallObject.Action.RETRY -> slack.model.Call.Action.RETRY
        slack.api.schemas.CallObject.Action.DECLINE -> slack.model.Call.Action.DECLINE
        slack.api.schemas.CallObject.Action.JOIN -> slack.model.Call.Action.JOIN
        else -> slack.model.Call.Action.UNKNOWN
        }
        }
        """
          .trimIndent()
      )
  }

  @Test
  fun testGenerateBody_Model() {
    myFixture.configureByFiles("CallTranslator.kt", "Call.kt")

    val body = generateBody()

    assertThat(body).isNotNull()
    assertThat(body!!.text)
      .isEqualTo(
        """
        {
        return Call($ASSIGNMENTS)
        }
        """
          .trimIndent()
      )
  }

  @Test
  fun testGenerateBody_Model_FqNameDestination() {
    myFixture.configureByFiles("FullyQualifiedCallTranslator.kt", "Call.kt")

    val body = generateBody()

    assertThat(body).isNotNull()
    assertThat(body!!.text)
      .isEqualTo(
        """
        {
        return slack.model.Call($ASSIGNMENTS)
        }
        """
          .trimIndent()
      )
  }

  @Test
  fun testGenerateBody_Model_ImportAlias() {
    myFixture.configureByFiles("ImportAliasCallTranslator.kt", "Call.kt")

    val body = generateBody()

    assertThat(body).isNotNull()
    assertThat(body!!.text)
      .isEqualTo(
        """
        {
        return DomainCall($ASSIGNMENTS)
        }
        """
          .trimIndent()
      )
  }

  private fun getNamedFunction() = myFixture.file.findDescendantOfType<KtNamedFunction>()!!

  private fun generateBody(): KtBlockExpression? {
    return TranslatorHelper.generateBody(TranslatorHelper.extractBundle(getNamedFunction())!!)
  }
}
