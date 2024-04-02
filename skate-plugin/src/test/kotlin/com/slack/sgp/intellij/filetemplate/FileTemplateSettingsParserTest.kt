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
      val templateStream =
        this.javaClass.classLoader.getResourceAsStream("test_malformed_file_templates_setting.yaml")
      templateStream?.let { SettingsParser(it).getTemplates() }
    }
  }
}
