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

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import io.mockk.mockk
import org.junit.Test

class FeatureFlagExtractorTest{

  private fun createKtPsiFileFromContent(project: Project, content: String): PsiFile {
    val fileType = FileTypeManager.getInstance().getFileTypeByExtension("kt")
    val psiFactory = PsiFileFactory.getInstance(project)
    return psiFactory.createFileFromText("Temporary.kt", fileType, content)
  }

  @Test
  fun testExtractFeatureFlags() {
    val fileContent =
      """
        @FeatureFlags(ForBlockKitFeature::class)
        enum class BlockKitFeature : FeatureFlagEnum {

          @FeatureFlag(defaultValue = false, minimization = FeatureFlag.Minimization.AUTHENTICATED)
          DATE_TIME_ELEMENT_BLOCK_CLIENT,

          @FeatureFlag(defaultValue = false, minimization = FeatureFlag.Minimization.AUTHENTICATED)
          URL_INPUT_ELEMENT_BLOCK_CLIENT,

          @FeatureFlag(defaultValue = false, minimization = FeatureFlag.Minimization.AUTHENTICATED)
          EMAIL_INPUT_ELEMENT_BLOCK_CLIENT,

          @FeatureFlag(defaultValue = false, minimization = FeatureFlag.Minimization.AUTHENTICATED)
          RICH_TEXT_ELEMENT_BLOCK_CLIENT,

          @FeatureFlag(defaultValue = false, minimization = FeatureFlag.Minimization.AUTHENTICATED)
          ANDROID_INPUT_BOTTOM_SHEET_REVAMP,

          // Leave the `;` at the end to avoid meaningless-diff/conflict
          ;

          override val key by computeKey()
        }

        @Qualifier
        @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD)
        annotation class ForBlockKitFeature(val feature: BlockKitFeature)
        """
        .trimIndent()

    // Mock a Project instance
    val mockProject = mockk<Project>(relaxed = true)
    // Create a PSI representation of the content and extract feature flags
    val psiFile = createKtPsiFileFromContent(mockProject, fileContent)
    val extractor = FeatureFlagExtractor()
    val featureFlags = extractor.extractFeatureFlags(psiFile)

    // Verifying the correct extraction of feature flags.
    assertThat(featureFlags).contains("DATE_TIME_ELEMENT_BLOCK_CLIENT")
    assertThat(featureFlags).contains("URL_INPUT_ELEMENT_BLOCK_CLIENT")
    assertThat(featureFlags).contains("EMAIL_INPUT_ELEMENT_BLOCK_CLIENT")
    assertThat(featureFlags).contains("RICH_TEXT_ELEMENT_BLOCK_CLIENT")
    assertThat(featureFlags).contains("ANDROID_INPUT_BOTTOM_SHEET_REVAMP")
  }
}
