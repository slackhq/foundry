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

class ChangeLogParserTest {
  // test with no entries and null changelogstring
  @Test
  fun testNoEntries() {
    val (changeLogString, latestEntry) = ChangelogParser.readFile("", null)
    assertThat(changeLogString).isNull()
    assertThat(latestEntry).isEqualTo(LocalDate.now())
  }

  // test with one entry, no previous entry
  @Test
  fun testSingleEntryNullPreviousEntry() {
    val input = "2023-06-28\nBug fixes\nNew features"
    val expectedDate = LocalDate.of(2023, 6, 28)
    val (changeLogString, latestEntry) = ChangelogParser.readFile(input, null)
    assertThat(changeLogString).isNull()
    assertThat(latestEntry).isEqualTo(expectedDate)
  }

  // test with mutliple entries, and no previous entry
  @Test
  fun testMultipleEntriesNullPreviousEntry() {
    val input = "2023-06-28\nBug fixes\nNew features\n2023-06-27\nOther changes"
    val expectedDate = LocalDate.of(2023, 6, 28)
    val expectedString = "2023-06-28\nBug fixes\nNew features"
    val (changeLogString, latestEntry) = ChangelogParser.readFile(input, null)
    assertThat(changeLogString).isEqualTo(expectedString)
    assertThat(latestEntry).isEqualTo(expectedDate)
  }

  // test with multiple entries, where the previous is the same as the latest
  @Test
  fun testPreviousEntrySameAsLatest() {
    val input = "2023-06-28\nBug fixes\nNew features\n2023-06-27\nOther changes"
    val expectedDate = LocalDate.of(2023, 6, 28)
    val (changeLogString, latestEntry) = ChangelogParser.readFile(input, LocalDate.of(2023, 6, 28))
    assertThat(changeLogString).isNull()
    assertThat(latestEntry).isEqualTo(expectedDate)
  }

  // test with a previous entry not in the change log
  @Test
  fun testPreviousEntryNotInChangeLog() {
    val input = "2023-06-28\nBug fixes\nNew features\n2023-06-27\nOther changes"
    val expectedDate = LocalDate.of(2023, 6, 28)
    val expectedString = "2023-06-28\nBug fixes\nNew features\n2023-06-27\nOther changes"
    val (changeLogString, latestEntry) = ChangelogParser.readFile(input, LocalDate.of(2023, 6, 29))
    assertThat(changeLogString).isEqualTo(expectedString)
    assertThat(latestEntry).isEqualTo(expectedDate)
  }
}
