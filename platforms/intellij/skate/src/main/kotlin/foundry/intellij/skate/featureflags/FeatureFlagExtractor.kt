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
package foundry.intellij.skate.featureflags

import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import foundry.intellij.skate.SkatePluginSettings
import java.util.Locale
import org.jetbrains.uast.UEnumConstant
import org.jetbrains.uast.UFile
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.toUElementOfType

/**
 * Responsible for extracting feature flags. Searches for enum entries annotated with 'FeatureFlag'
 * to identify feature flags.
 */
object FeatureFlagExtractor {

  /**
   * Extracts the names of feature flags from the provided PSI file. Only processes Kotlin files.
   *
   * @param psiFile The PSI representation of the file to process.
   */
  fun extractFeatureFlags(psiFile: PsiFile): List<FeatureFlagSymbol> {
    val settings = psiFile.project.service<SkatePluginSettings>()
    val baseUrl = settings.featureFlagBaseUrl
    val flagAnnotation = settings.featureFlagAnnotation.orEmpty()

    val uFile = psiFile.toUElementOfType<UFile>() ?: return emptyList()

    return uFile
      .allClassesAndInnerClasses()
      .filter { it.isEnum }
      .flatMap { enumClass -> enumClass.uastDeclarations.filterIsInstance<UEnumConstant>() }
      .mapNotNull { enumConstant ->
        enumConstant.findAnnotation(flagAnnotation)?.let { flagAnnotation ->
          val textRange = enumConstant.sourcePsi?.textRange ?: return@mapNotNull null
          val key =
            flagAnnotation.findAttributeValue("key")?.evaluateString()?.takeUnless {
              it.trim().isBlank()
            } ?: enumConstant.name.lowercase(Locale.US)
          FeatureFlagSymbol(textRange, "$baseUrl$key")
        }
      }
      .toList()
  }
}

data class FeatureFlagSymbol(val range: TextRange, val url: String)
