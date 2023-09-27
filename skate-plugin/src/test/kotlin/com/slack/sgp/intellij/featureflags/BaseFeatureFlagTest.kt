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

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase

abstract class BaseFeatureFlagTest : LightPlatformCodeInsightFixture4TestCase() {
  // language=kotlin
  protected val fileContent =
    """
        enum class TestFeatures :
          FeatureFlagEnum {
          @Deprecated("test")
          @FeatureFlag(defaultValue = false, key ="test_flag_one", minimization = AUTHENTICATED)
          FLAG_ONE,
          @FeatureFlag(defaultValue = false, minimization = AUTHENTICATED)
          FLAG_TWO,
          @FeatureFlag(defaultValue = false, minimization = AUTHENTICATED)
          FLAG_THREE,
        }
    """

  protected fun createKotlinFile(
    name: String,
    text: String,
  ): PsiFile {
    return myFixture.configureByText(name, text)
  }
}
