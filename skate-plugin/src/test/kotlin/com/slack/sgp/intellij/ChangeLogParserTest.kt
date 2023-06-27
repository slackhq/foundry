package com.slack.sgp.intellij

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class ChangeLogParserTest {
  @Test
  fun testNoEntries() {
    val (changeLogString, latestEntry) = ChangelogParser.readFile("", null)
    assertThat(changeLogString).isNull()
    assertThat(latestEntry).isEqualTo(LocalDate.now())
  }

  @Test
  fun testNullPreviousEntry() {
    val initialChangeLogString = "2023-06-27"
    val (changeLogString, latestEntry) = ChangelogParser.readFile(initialChangeLogString, null)
    assertThat(changeLogString).isNull()
    assertThat(latestEntry).isEqualTo(LocalDate.of(2023, 6, 27))
  }

  @Test
  fun testChangeLogStringIsNotNull() {
    val initialChangeLogString =
      """
            Changelog
            =========

            0.9.14
            ------

            _2023-06-25_ 

            * Fix compose compiler config not applying to android projects.

            0.9.13
            ------

            _2023-06-24_

            * Fix wrong map key name being used in exclusion.

            0.9.12
            ------

            _2023-06-24_

            * Fix wrong dependency being used for compose-compiler in new Compose configuration overhaul.
        """
        .trimIndent()

    val previous = LocalDate.of(2023, 6, 24) // This date is prior to the latest entry date

    val (changeLogString, latestEntry) = ChangelogParser.readFile(initialChangeLogString, previous)

    assertThat(changeLogString).isNull()
    assertThat(latestEntry).isEqualTo(LocalDate.of(2023, 6, 25))
  }

  @Test
  fun testChangeLogStringWithNonexistentPreviousEntry() {
    val initialChangeLogString =
      """
          Changelog
          =========

          0.9.14
          ------

          _2023-06-25_

          * Fix compose compiler config not applying to android projects.

          0.9.13
          ------

          _2023-06-24_

          * Fix wrong map key name being used in exclusion.

          0.9.12
          ------

          _2023-06-24_

          * Fix wrong dependency being used for compose-compiler in new Compose configuration overhaul.
      """
        .trimIndent()

    val previousEntry = LocalDate.of(2023, 6, 24)

    val (changeLogString, latestEntry) =
      ChangelogParser.readFile(initialChangeLogString, previousEntry)

    assertThat(changeLogString).isNotNull()
    assertThat(latestEntry).isEqualTo(LocalDate.of(2023, 6, 25))
  }

  // TODO:
  //  ChangeLogString with an entry but null previous entry
  //  ChangeLogString with latest entry = to previous entry
  //  ChangeLogString where previous entry is not null, but not present in ChangeLog
  //  ChangeLogString where previous entry is not null, not present in ChangeLog, and
}
