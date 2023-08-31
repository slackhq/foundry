package com.slack.sgp.intellij.featureflags

import com.google.common.truth.Truth.assertThat
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.ServiceManager
import com.intellij.testFramework.TestIndexingModeSupporter
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Test
import org.jetbrains.kotlin.psi.KtPsiFactory
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.KotlinLanguage
import io.mockk.mockk
import org.jetbrains.kotlin.idea.KotlinFileType

class FeatureFlagExtractorTest {

  fun createPsiFromContent(project: Project, content: String): KtFile {
    val fileType = FileTypeManager.getInstance().getFileTypeByExtension("kt")

//    val instance = PsiFileFactory.getInstance(project)
//    if (instance !is PsiFileFactory) {
//      throw IllegalStateException("Unexpected type: ${instance::class.java.name}")
//    }
    val fileFactory = project.getService(PsiFileFactory::class.java)



//    val fileFactory: PsiFileFactory = PsiFileFactory.getInstance(project)
    val psiFile: PsiFile = fileFactory.createFileFromText("Temporary.kt", fileType, content)

//    if (psiFile !is KtFile) {
//      throw IllegalArgumentException("Failed to create KtFile")
//    }

    return psiFile
  }

  @Test
  fun testExtractFeatureFlags() {
    val fileContent = """
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
        """.trimIndent()

    // Mock a Project instance
    val mockProject = mockk<Project>(relaxed = true)
    // When: We create a PSI representation of the content and extract feature flags
    val psiFile = createPsiFromContent(mockProject, fileContent)
    val extractor = FeatureFlagExtractor()
    val featureFlags = extractor.extractFeatureFlags(psiFile as PsiFile)


    // Verifying the correct extraction of feature flags.
    assertThat(featureFlags).contains("DATE_TIME_ELEMENT_BLOCK_CLIENT")
    assertThat(featureFlags).contains("URL_INPUT_ELEMENT_BLOCK_CLIENT")
    assertThat(featureFlags).contains("EMAIL_INPUT_ELEMENT_BLOCK_CLIENT")
    assertThat(featureFlags).contains("RICH_TEXT_ELEMENT_BLOCK_CLIENT")
    assertThat(featureFlags).contains("ANDROID_INPUT_BOTTOM_SHEET_REVAMP")
  }

}
