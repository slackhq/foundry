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

class CodeOwnerRepositoryTest : BasePlatformTestCase() {

  fun testMissingFile() {
    val underTest = createCodeOwnerRepository(FakeCodeOwnerFileFetcherImpl(path = "boguspath"))
    assertThat(underTest.getCodeOwnership("any")).isEmpty()
  }

  fun testSingleMatchingResult() {
    val underTest = createCodeOwnerRepository()
    assertThat(underTest.getCodeOwnership("app/folder2/subfolder/foo.kt").size).isEqualTo(1)
  }

  fun testMultipleMatchingResult() {
    val underTest = createCodeOwnerRepository()
    assertThat(underTest.getCodeOwnership("app/folder3/subfolder/foo.kt").size).isEqualTo(2)
  }

  fun testNoResult() {
    val underTest = createCodeOwnerRepository()
    assertThat(underTest.getCodeOwnership("pathnotinfile")).isEmpty()
  }

  private fun createCodeOwnerRepository(
    codeOwnerFileFetcher: CodeOwnerFileFetcher =
      FakeCodeOwnerFileFetcherImpl("src/test/resources/test-code-ownership.csv")
  ): CodeOwnerRepository {
    return CodeOwnerRepository(codeOwnerFileFetcher)
  }
}
