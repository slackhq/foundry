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
package foundry.intellij.skate

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class ChangeLogParserTest {
  @Test
  fun `no entries and null changelog string`() {
    val (changeLogString, latestEntry) = ChangelogParser.readFile("", null)
    assertThat(changeLogString).isNull()
    assertThat(latestEntry).isEqualTo(LocalDate.now())
  }

  @Test
  fun `one entry, no previous entries`() {
    val input =
      """
      Changelog
      =========

      2023-07-07
      ----------

      - Don't register `RakeDependencies` task on platform projects.
      - Fix configuration cache for Dependency Rake. Note that DAGP doesn't yet support it.
      - Add Dependency Rake usage to its doc.
      - Add missing identifiers aggregation for Dependency Rake. This makes it easier to find and add missing identifiers to version catalogs that dependency rake expects.
        - `./gradlew aggregateMissingIdentifiers -Pslack.gradle.config.enableAnalysisPlugin=true --no-configuration-cache`
      """
        .trimIndent()

    val expectedDate = LocalDate.of(2023, 7, 7)
    val (changeLogString, latestEntry) = ChangelogParser.readFile(input, null)
    assertThat(changeLogString).isEqualTo(input)
    assertThat(latestEntry).isEqualTo(expectedDate)
  }

  @Test
  fun `multiple entries, and no previous entries`() {
    val input =
      """
      2023-06-29
      ----------

      - Switch Robolectric jar downloads to use detached configurations.
        - This lets Gradle do the heavy lifting of caching the downloaded jars and also allows downloading them from a configured repository setup. This also simplifies the up-to-date checks.
      - Docs are now published on https://slackhq.github.io/foundry. This is a work in progress.
      - API kdocs are published at https://slackhq.github.io/foundry/api/0.x/.
      - Update `kotlin-cli-util` to 1.2.2.

      2023-06-25
      ----------

      * Fix compose compiler config not applying to android projects.
      """
        .trimIndent()

    val expected =
      """
      2023-06-29
      ----------

      - Switch Robolectric jar downloads to use detached configurations.
        - This lets Gradle do the heavy lifting of caching the downloaded jars and also allows downloading them from a configured repository setup. This also simplifies the up-to-date checks.
      - Docs are now published on https://slackhq.github.io/foundry. This is a work in progress.
      - API kdocs are published at https://slackhq.github.io/foundry/api/0.x/.
      - Update `kotlin-cli-util` to 1.2.2.
      """
        .trimIndent()

    val expectedDate = LocalDate.of(2023, 6, 29)
    val (changeLogString, latestEntry) = ChangelogParser.readFile(input, null)
    assertThat(changeLogString).isEqualTo(expected)
    assertThat(latestEntry).isEqualTo(expectedDate)
  }

  @Test
  fun `multiple entries and match is in the middle`() {
    val input =
      """
      2023-06-29
      ----------

      - Switch Robolectric jar downloads to use detached configurations.
        - This lets Gradle do the heavy lifting of caching the downloaded jars and also allows downloading them from a configured repository setup. This also simplifies the up-to-date checks.
      - Docs are now published on https://slackhq.github.io/foundry. This is a work in progress.
      - API kdocs are published at https://slackhq.github.io/foundry/api/0.x/.
      - Update `kotlin-cli-util` to 1.2.2.

      2023-06-25
      ----------

      * Fix compose compiler config not applying to android projects.
      """
        .trimIndent()

    val expectedDate = LocalDate.of(2023, 6, 29)
    val expectedString =
      """
      2023-06-29
      ----------

      - Switch Robolectric jar downloads to use detached configurations.
        - This lets Gradle do the heavy lifting of caching the downloaded jars and also allows downloading them from a configured repository setup. This also simplifies the up-to-date checks.
      - Docs are now published on https://slackhq.github.io/foundry. This is a work in progress.
      - API kdocs are published at https://slackhq.github.io/foundry/api/0.x/.
      - Update `kotlin-cli-util` to 1.2.2.
      """
        .trimIndent()
    val (changeLogString, latestEntry) = ChangelogParser.readFile(input, LocalDate.of(2023, 6, 25))
    assertThat(changeLogString).isEqualTo(expectedString)
    assertThat(latestEntry).isEqualTo(expectedDate)
  }

  @Test
  fun `multiple entries, where the previous is the same as the latest`() {
    val input =
      """
      Changelog
      =========

      2023-07-07
      ----------

      - Don't register `RakeDependencies` task on platform projects.
      - Fix configuration cache for Dependency Rake. Note that DAGP doesn't yet support it.
      - Add Dependency Rake usage to its doc.
      - Add missing identifiers aggregation for Dependency Rake. This makes it easier to find and add missing identifiers to version catalogs that dependency rake expects.
        - `./gradlew aggregateMissingIdentifiers -Pslack.gradle.config.enableAnalysisPlugin=true --no-configuration-cache`

      2023-06-30
      ----------

      - Enable lint on test sources by default.
      - Account for all version catalogs in `DependencyRake`.
      - Update Guava to `32.1.0-jre`.
      """
        .trimIndent()
    val expectedDate = LocalDate.of(2023, 7, 7)
    val (changeLogString, latestEntry) = ChangelogParser.readFile(input, LocalDate.of(2023, 7, 7))
    assertThat(changeLogString).isNull()
    assertThat(latestEntry).isEqualTo(expectedDate)
  }

  @Test
  fun `test with a previous entry not in the change log`() {
    val input =
      """
      Changelog
      =========

      2023-07-16
      ----------

      - Don't register `RakeDependencies` task on platform projects.
      - Fix configuration cache for Dependency Rake. Note that DAGP doesn't yet support it.
      - Add Dependency Rake usage to its doc.
      - Add missing identifiers aggregation for Dependency Rake. This makes it easier to find and add missing identifiers to version catalogs that dependency rake expects.
        - `./gradlew aggregateMissingIdentifiers -Pslack.gradle.config.enableAnalysisPlugin=true --no-configuration-cache`

      2023-06-30
      ----------

      - Enable lint on test sources by default.
      - Account for all version catalogs in `DependencyRake`.
      - Update Guava to `32.1.0-jre`.
      """
        .trimIndent()
    val expected =
      """
      Changelog
      =========

      2023-07-16
      ----------

      - Don't register `RakeDependencies` task on platform projects.
      - Fix configuration cache for Dependency Rake. Note that DAGP doesn't yet support it.
      - Add Dependency Rake usage to its doc.
      - Add missing identifiers aggregation for Dependency Rake. This makes it easier to find and add missing identifiers to version catalogs that dependency rake expects.
        - `./gradlew aggregateMissingIdentifiers -Pslack.gradle.config.enableAnalysisPlugin=true --no-configuration-cache`
    """
        .trimIndent()
    val expectedDate = LocalDate.of(2023, 7, 16)
    val (changeLogString, latestEntry) = ChangelogParser.readFile(input, LocalDate.of(2023, 7, 15))
    assertThat(changeLogString).isEqualTo(expected)
    assertThat(latestEntry).isEqualTo(expectedDate)
  }

  @Test
  fun `multiple entries, previous entry matches but not the latest`() {
    val input =
      """
        Changelog
        =========

        2023-07-07
        ----------

        - Don't register `RakeDependencies` task on platform projects.
        - Fix configuration cache for Dependency Rake. Note that DAGP doesn't yet support it.
        - Add Dependency Rake usage to its doc.
        - Add missing identifiers aggregation for Dependency Rake. This makes it easier to find and add missing identifiers to version catalogs that dependency rake expects.
          - `./gradlew aggregateMissingIdentifiers -Pslack.gradle.config.enableAnalysisPlugin=true --no-configuration-cache`

        2023-06-30
        ----------

        - Enable lint on test sources by default.
        - Account for all version catalogs in `DependencyRake`.
        - Update Guava to `32.1.0-jre`.

        2023-06-29
        ----------

        - Switch Robolectric jar downloads to use detached configurations.
          - This lets Gradle do the heavy lifting of caching the downloaded jars and also allows downloading them from a configured repository setup. This also simplifies the up-to-date checks.
        - Docs are now published on https://slackhq.github.io/foundry. This is a work in progress.
        - API kdocs are published at https://slackhq.github.io/foundry/api/0.x/.
        - Update `kotlin-cli-util` to 1.2.2.
        """
        .trimIndent()

    val expectedDate = LocalDate.of(2023, 7, 7)
    val expectedString =
      """
        Changelog
        =========

        2023-07-07
        ----------

        - Don't register `RakeDependencies` task on platform projects.
        - Fix configuration cache for Dependency Rake. Note that DAGP doesn't yet support it.
        - Add Dependency Rake usage to its doc.
        - Add missing identifiers aggregation for Dependency Rake. This makes it easier to find and add missing identifiers to version catalogs that dependency rake expects.
          - `./gradlew aggregateMissingIdentifiers -Pslack.gradle.config.enableAnalysisPlugin=true --no-configuration-cache`

        2023-06-30
        ----------

        - Enable lint on test sources by default.
        - Account for all version catalogs in `DependencyRake`.
        - Update Guava to `32.1.0-jre`.
        """
        .trimIndent()

    val (changeLogString, latestEntry) = ChangelogParser.readFile(input, LocalDate.of(2023, 6, 29))

    assertThat(changeLogString).isEqualTo(expectedString)
    assertThat(latestEntry).isEqualTo(expectedDate)
  }

  @Test
  fun `date in changelog with no italics`() {
    val input =
      """
      Changelog
      =========

      2023-07-07

      - Don't register `RakeDependencies` task on platform projects.
      - Fix configuration cache for Dependency Rake. Note that DAGP doesn't yet support it.
      - Add Dependency Rake usage to its doc.
      - Add missing identifiers aggregation for Dependency Rake. This makes it easier to find and add missing identifiers to version catalogs that dependency rake expects.
        - `./gradlew aggregateMissingIdentifiers -Pslack.gradle.config.enableAnalysisPlugin=true --no-configuration-cache`
      """
        .trimIndent()

    val expectedDate = LocalDate.of(2023, 7, 7)
    val (changeLogString, latestEntry) = ChangelogParser.readFile(input, null)
    assertThat(changeLogString).isEqualTo(input)
    assertThat(latestEntry).isEqualTo(expectedDate)
  }
}
