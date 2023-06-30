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
package com.slack.sgp.intellij

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChangeLogPresenterTest {
  @Test
  fun `first time I'm looking at skate plugin, I should see the entire changelog`() {
    val input =
      """
      2023-06-28

      * Bug fixes
      * New features
      """
        .trimIndent()

    val changeLogPresenter = ChangelogPresenter()

    val changeLogString = changeLogPresenter.readFile(input)
    assertThat(changeLogString).isEqualTo(input)
  }

  @Test
  fun `second time I'm looking at skate plugin where the changelog file is the same, I shouldn't see anything`() {
    val input =
      """
      2023-06-28

      * Bug fixes
      * New features
      """
        .trimIndent()

    val changeLogPresenter = ChangelogPresenter()

    val firstChangeLogString = changeLogPresenter.readFile(input)
    val secondChangeLogString = changeLogPresenter.readFile(input)

    assertThat(firstChangeLogString).isEqualTo(input)
    assertThat(secondChangeLogString).isNull()
  }
}
