package com.slack.sgp.intellij.filetemplate

import com.charleskorn.kaml.MissingRequiredPropertyException
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.UsefulTestCase.assertThrows
import com.slack.sgp.intellij.filetemplate.model.SettingsParser
import org.junit.Test

class FileTemplateSettingsParserTest {
  @Test
  fun testSuccessfulParse() {
    val templateStream = this.javaClass.classLoader.getResourceAsStream("test_file_templates.yaml")
    val templates = templateStream?.let { SettingsParser(it).getTemplates() }
    assertThat(templates).isNotNull()
    assertThat(templates?.size).isEqualTo(2)
    assertThat(templates?.get("Template1")?.fileNameSuffix).isEqualTo("Main")
    assertThat(templates?.get("Template2")?.fileNameSuffix).isEqualTo("Test")
  }

  @Test
  fun testMissingProperties() {
    assertThrows(MissingRequiredPropertyException::class.java) {
      val templateStream = this.javaClass.classLoader.getResourceAsStream("test_malformed_file_templates_setting.yaml")
      templateStream?.let { SettingsParser(it).getTemplates() }
    }
  }
}