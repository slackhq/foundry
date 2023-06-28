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
  fun testSingleEntryNullPreviousEntry() {
    val input = "2023-06-28\nBug fixes\nNew features"
    val expectedDate = LocalDate.of(2023, 6, 28)
    val (changeLogString, latestEntry) = ChangelogParser.readFile(input, null)
    assertThat(changeLogString).isNull()
    assertThat(latestEntry).isEqualTo(expectedDate)
  }

  @Test
  fun testMultipleEntriesNullPreviousEntry() {
    val input = "2023-06-28\nBug fixes\nNew features\n2023-06-27\nOther changes"
    val expectedDate = LocalDate.of(2023, 6, 28)
    val expectedString = "2023-06-28\nBug fixes\nNew features"
    val (changeLogString, latestEntry) = ChangelogParser.readFile(input, null)
    assertThat(changeLogString).isEqualTo(expectedString)
    assertThat(latestEntry).isEqualTo(expectedDate)
  }

  @Test
  fun testPreviousEntrySameAsLatest() {
    val input = "2023-06-28\nBug fixes\nNew features\n2023-06-27\nOther changes"
    val expectedDate = LocalDate.of(2023, 6, 28)
    val (changeLogString, latestEntry) = ChangelogParser.readFile(input, LocalDate.of(2023, 6, 28))
    assertThat(changeLogString).isNull()
    assertThat(latestEntry).isEqualTo(expectedDate)
  }

  @Test
  fun testPreviousEntryNotInChangeLog() {
    val input = "2023-06-28\nBug fixes\nNew features\n2023-06-27\nOther changes"
    val expectedDate = LocalDate.of(2023, 6, 28)
    val expectedString = "2023-06-28\nBug fixes\nNew features\n2023-06-27\nOther changes"
    val (changeLogString, latestEntry) = ChangelogParser.readFile(input, LocalDate.of(2023, 6, 29))
    assertThat(changeLogString).isEqualTo(expectedString)
    assertThat(latestEntry).isEqualTo(expectedDate)
  }

  // TODO:
  //  ChangeLogString with an entry but null previous entry
  //  ChangeLogString with latest entry = to previous entry
  //  ChangeLogString where previous entry is not null, but not present in ChangeLog
  //  ChangeLogString where previous entry is not null, not present in ChangeLog, and
}
