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
    val initialState = ChangelogPresenter.State(lastReadDate = LocalDate.of(2023, 6, 30))
    presenter.loadState(initialState)

    val expectedDate = LocalDate.of(2023, 7, 7)
    val result = presenter.readFile(input)

    // Accessing private properties for test might require additional setup or changes to the code.
    val lastReadDate = presenter.getState().lastReadDate

    assertThat(result?.changeLogString).isEqualTo(expectedResult)
    assertThat(lastReadDate).isEqualTo(expectedDate)
  }
}
