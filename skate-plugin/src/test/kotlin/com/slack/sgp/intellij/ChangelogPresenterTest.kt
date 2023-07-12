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
import java.time.LocalDate
import org.junit.Test

class ChangelogPresenterTest {
  @Test
  fun `no entries and no changelogString`() {
    val presenter = ChangelogPresenter()

    val result = presenter.readFile("")
    assertThat(result).isNull()
  }

  @Test
  fun `one entry, no previous entries`() {
    val input =
      """
      Changelog
      =========

      0.9.17
      ------

      _2023-07-07_

      - Don't register `RakeDependencies` task on platform projects.
      - Fix configuration cache for Dependency Rake. Note that DAGP doesn't yet support it.
      - Add Dependency Rake usage to its doc.
      - Add missing identifiers aggregation for Dependency Rake. This makes it easier to find and add missing identifiers to version catalogs that dependency rake expects.
      - `./gradlew aggregateMissingIdentifiers -Pslack.gradle.config.enableAnalysisPlugin=true --no-configuration-cache`
      """
        .trimIndent()

    val presenter = ChangelogPresenter()
    val initialState = ChangelogPresenter.State(lastReadDate = null)
    presenter.loadState(initialState)

    val expectedDate = LocalDate.of(2023, 7, 7)
    val result = presenter.readFile(input)

    // Accessing private properties for test might require additional setup or changes to the code.
    val lastReadDate = presenter.getState().lastReadDate

    assertThat(result).isEqualTo(input)
    assertThat(lastReadDate).isEqualTo(expectedDate)
  }

  @Test
  fun `one entry, one previous entry`() {
    val input =
      """
      Changelog
      =========

      0.9.17
      ------

      _2023-07-07_

      - Don't register `RakeDependencies` task on platform projects.
      - Fix configuration cache for Dependency Rake. Note that DAGP doesn't yet support it.
      - Add Dependency Rake usage to its doc.
      - Add missing identifiers aggregation for Dependency Rake. This makes it easier to find and add missing identifiers to version catalogs that dependency rake expects.
      - `./gradlew aggregateMissingIdentifiers -Pslack.gradle.config.enableAnalysisPlugin=true --no-configuration-cache`
      """
        .trimIndent()

    val presenter = ChangelogPresenter()
    val initialState = ChangelogPresenter.State(lastReadDate = LocalDate.of(2023, 7, 7))
    presenter.loadState(initialState)

    val expectedDate = LocalDate.of(2023, 7, 7)
    val result = presenter.readFile(input)

    // Accessing private properties for test might require additional setup or changes to the code.
    val lastReadDate = presenter.getState().lastReadDate

    assertThat(result?.changeLogString).isEqualTo("")
    assertThat(lastReadDate).isEqualTo(expectedDate)
  }

  @Test
  fun `two entries, one previous entry`() {
    val input =
      """
      Changelog
=========

0.9.17
------

_2023-07-07_

- Don't register `RakeDependencies` task on platform projects.
- Fix configuration cache for Dependency Rake. Note that DAGP doesn't yet support it.
- Add Dependency Rake usage to its doc.
- Add missing identifiers aggregation for Dependency Rake. This makes it easier to find and add missing identifiers to version catalogs that dependency rake expects.
  - `./gradlew aggregateMissingIdentifiers -Pslack.gradle.config.enableAnalysisPlugin=true --no-configuration-cache`

0.9.16
------

_2023-06-30_

- Enable lint on test sources by default.
- Account for all version catalogs in `DependencyRake`.
- Update Guava to `32.1.0-jre`.

0.9.15
------

_2023-06-29_

- Switch Robolectric jar downloads to use detached configurations.
  - This lets Gradle do the heavy lifting of caching the downloaded jars and also allows downloading them from a configured repository setup. This also simplifies the up-to-date checks.
- Docs are now published on https://slackhq.github.io/slack-gradle-plugin. This is a work in progress.
- API kdocs are published at https://slackhq.github.io/slack-gradle-plugin/api/0.x/.
- Update `kotlin-cli-util` to 1.2.2.

0.9.14
------

_2023-06-25_

* Fix compose compiler config not applying to android projects.

0.9.13
------

_2023-06-24_

* Fix wrong map key name being used in exclusion.
      """
        .trimIndent()

    val expectedResult =
      """
      Changelog
      =========

      0.9.17
      ------

      _2023-07-07_

      - Don't register `RakeDependencies` task on platform projects.
      - Fix configuration cache for Dependency Rake. Note that DAGP doesn't yet support it.
      - Add Dependency Rake usage to its doc.
      - Add missing identifiers aggregation for Dependency Rake. This makes it easier to find and add missing identifiers to version catalogs that dependency rake expects.
      - `./gradlew aggregateMissingIdentifiers -Pslack.gradle.config.enableAnalysisPlugin=true --no-configuration-cache`
      """
        .trimIndent()

    val presenter = ChangelogPresenter()
    val initialState = ChangelogPresenter.State(lastReadDate = LocalDate.of(2023, 6, 25))
    presenter.loadState(initialState)

    val expectedDate = LocalDate.of(2023, 7, 7)
    val result = presenter.readFile(input)

    // Accessing private properties for test might require additional setup or changes to the code.
    val lastReadDate = presenter.getState().lastReadDate

    assertThat(result?.changeLogString).isEqualTo(expectedResult)
    assertThat(lastReadDate).isEqualTo(expectedDate)
  }
}
