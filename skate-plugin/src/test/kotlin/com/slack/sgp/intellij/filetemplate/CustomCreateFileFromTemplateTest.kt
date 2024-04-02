/*
 * Copyright (C) 2024 Slack Technologies, LLC
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
package com.slack.sgp.intellij.filetemplate

import com.intellij.ide.fileTemplates.impl.CustomFileTemplate
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CustomCreateFileFromTemplateTest : BasePlatformTestCase() {
  private val testTemplate =
    CustomFileTemplate("test template", "kt").apply {
      text =
        """
        #if (${'$'}{PACKAGE_NAME} && ${'$'}{PACKAGE_NAME} != "")package ${'$'}{PACKAGE_NAME}

        #end
        @Parcelize
        class ${'$'}{NAME}Screen : Screen {
        }
    """
          .trimIndent()
    }

  private val circuitTemplate =
    CustomFileTemplate("Circuit Presenter (without UI)", "kt").apply {
      text =
        """
        #if (${'$'}{PACKAGE_NAME} && ${'$'}{PACKAGE_NAME} != "")package ${'$'}{PACKAGE_NAME}

        #end
        @Parcelize
        class ${'$'}{NAME}Screen : Screen {
        }
    """
          .trimIndent()
    }

  fun testClassCreatedFromTemplate() =
    doTest(
      template = testTemplate,
      userInput = "MyClass",
      expectedFileName = "MyClass.kt",
      expectedClassName = "MyClass",
    )

  fun testClassNameCreatedWithSuffix() =
    doTest(
      template = circuitTemplate,
      userInput = "MyClass",
      expectedFileName = "MyClassScreen.kt",
      expectedClassName = "MyClass",
    )

  private fun doTest(
    template: CustomFileTemplate,
    userInput: String,
    existentPath: String? = null,
    expectedFileName: String,
    expectedClassName: String,
  ) {
    if (existentPath != null) {
      myFixture.tempDirFixture.findOrCreateDir(existentPath)
    }

    val actDir = myFixture.psiManager.findDirectory(myFixture.tempDirFixture.findOrCreateDir("."))!!
    val file = CreateCircuitFeature().createFileFromTemplate(userInput, template, actDir)!!

    assertEquals(expectedFileName, file.name)

    val expectedContent =
      """
        @Parcelize
        class ${expectedClassName}Screen : Screen {
        }
    """
        .trimIndent()
    assertEquals(expectedContent, file.text)
  }
}
