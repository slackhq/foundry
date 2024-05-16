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
package com.slack.sgp.intellij.codeowners

import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.fixtures.BasePlatformTestCase

private const val TEAM_1 = "Team 1"
private const val TEAM_2 = "Team 2"
private const val FOLDER_2_PATTERN = "app/folder2/.*"
private const val FOLDER_3_PATTERN = "app/folder3/.*"
private const val FOLDER_3_GRANULAR_PATTERN = "app/folder3/subfolder/.*"
private const val TEST_OWNERSHIP_YAML_FILE = "src/test/resources/test-code-ownership.yaml"

/**
 * Unit test for [CodeOwnerRepository]. Note that IntelliJ OpenFileDescriptor line focus is 0-based.
 */
class CodeOwnerRepositoryTest : BasePlatformTestCase() {

  fun testMissingFile() {
    val underTest = createCodeOwnerRepository(FakeCodeOwnerFileFetcherImpl(path = "boguspath"))
    assertThat(underTest.getCodeOwnership("any")).isEmpty()
  }

  fun testSingleMatchingResult() {
    val underTest = createCodeOwnerRepository()
    val result = underTest.getCodeOwnership("app/folder2/subfolder/foo.kt")
    assertThat(result.size).isEqualTo(1)
    assertThat(result[0].packagePattern).isEqualTo(FOLDER_2_PATTERN)
    assertThat(result[0].codeOwnerLineNumber).isEqualTo(15)
    assertThat(result[0].team).isEqualTo(TEAM_1)
  }

  fun testMultipleMatchingResult() {
    val underTest = createCodeOwnerRepository()
    val result = underTest.getCodeOwnership("app/folder3/subfolder/foo.kt")
    assertThat(result.size).isEqualTo(2)
    assertThat(result[0].packagePattern).isEqualTo(FOLDER_3_GRANULAR_PATTERN)
    assertThat(result[0].codeOwnerLineNumber).isEqualTo(16)
    assertThat(result[0].team).isEqualTo(TEAM_1)
    assertThat(result[1].packagePattern).isEqualTo(FOLDER_3_PATTERN)
    assertThat(result[1].codeOwnerLineNumber).isEqualTo(20)
    assertThat(result[1].team).isEqualTo(TEAM_2)
  }

  fun testNoResult() {
    val underTest = createCodeOwnerRepository()
    assertThat(underTest.getCodeOwnership("pathnotinfile")).isEmpty()
  }

  private fun createCodeOwnerRepository(
    codeOwnerFileFetcher: CodeOwnerFileFetcher =
      FakeCodeOwnerFileFetcherImpl(TEST_OWNERSHIP_YAML_FILE)
  ): CodeOwnerRepository {
    return CodeOwnerRepository(codeOwnerFileFetcher)
  }
}
