package com.slack.sgp.intellij.filetemplates

import com.intellij.ide.fileTemplates.impl.CustomFileTemplate
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.slack.sgp.intellij.filetemplate.CreateCircuitFeature

class CustomCreateFileFromTemplateTest : BasePlatformTestCase() {
  private val testTemplate = CustomFileTemplate("test template", "kt").apply {
    text = """
        #if (${'$'}{PACKAGE_NAME} && ${'$'}{PACKAGE_NAME} != "")package ${'$'}{PACKAGE_NAME}
        
        #end
        @Parcelize
        class ${'$'}{NAME}Screen : Screen {
        }
    """.trimIndent()
  }

  private val circuitTemplate = CustomFileTemplate("Circuit Presenter (without UI)", "kt").apply {
    text = """
        #if (${'$'}{PACKAGE_NAME} && ${'$'}{PACKAGE_NAME} != "")package ${'$'}{PACKAGE_NAME}
        
        #end
        @Parcelize
        class ${'$'}{NAME}Screen : Screen {
        }
    """.trimIndent()
  }

  fun testClassCreatedFromTemplate() = doTest(
    template = testTemplate,
    userInput = "MyClass",
    expectedFileName = "MyClass.kt",
    expectedClassName = "MyClass"
  )

  fun testClassNameCreatedWithSuffix() = doTest(
    template = circuitTemplate,
    userInput = "MyClass",
    expectedFileName = "MyClassScreen.kt",
    expectedClassName = "MyClass"
  )
  private fun doTest(
    template: CustomFileTemplate,
    userInput: String,
    existentPath: String? = null,
    expectedFileName: String,
    expectedClassName: String
  ) {
    if (existentPath != null) {
      myFixture.tempDirFixture.findOrCreateDir(existentPath)
    }

    val actDir = myFixture.psiManager.findDirectory(myFixture.tempDirFixture.findOrCreateDir("."))!!
    val file = CreateCircuitFeature().createFileFromTemplate(userInput, template, actDir)!!

    assertEquals(expectedFileName, file.name)

    val expectedContent = """
        @Parcelize
        class ${expectedClassName}Screen : Screen {
        }
    """.trimIndent()
    assertEquals(expectedContent, file.text)
  }
}