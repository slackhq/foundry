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
package com.slack.sgp.intellij.codeowners

import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.slack.sgp.intellij.util.settings

private const val TEST_OWNERSHIP_YAML_FILE = "src/test/resources/test-code-ownership.yaml"

class CodeOwnerFileFetcherImplTest : BasePlatformTestCase() {

  fun testGetCodeOwnershipFileSettingPresent() {
    val underTest = CodeOwnerFileFetcherImpl(project, this.basePath)
    project.settings().codeOwnerFilePath = TEST_OWNERSHIP_YAML_FILE
    project.settings().isCodeOwnerEnabled = true
    assertThat(underTest.getCodeOwnershipFile()).isNotNull()
  }

  fun testGetCodeOwnershipFileSettingNotPresent() {
    val underTest = CodeOwnerFileFetcherImpl(project, this.basePath)
    project.settings().codeOwnerFilePath = null
    project.settings().isCodeOwnerEnabled = true
    assertThat(underTest.getCodeOwnershipFile()).isNull()
  }
}
