/// *
// * Copyright (C) 2023 Slack Technologies, LLC
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *    https://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
// package com.slack.sgp.intellij
//
// import com.google.common.truth.Truth.assertThat
// import java.time.LocalDate
// import org.junit.Test
//
// class ChangeLogParserTest {
//  @Test
//  fun `no entries and null changelogstring`() {
//    val (changeLogString, latestEntry) = ChangelogParser.readFile("", null)
//    assertThat(changeLogString).isNull()
//    assertThat(latestEntry).isEqualTo(LocalDate.now())
//  }
//
//  @Test
//  fun `one entry, no previous entries`() {
//    val input =
//      """
//      2023-06-28
//
//      * Bug fixes
//      * New features
//      """
//        .trimIndent()
//
//    val expectedDate = LocalDate.of(2023, 6, 28)
//    val (changeLogString, latestEntry) = ChangelogParser.readFile(input, null)
//    assertThat(changeLogString).isNull()
//    assertThat(latestEntry).isEqualTo(expectedDate)
//  }
//
//  @Test
//  fun `mutliple entries, and no previous entries`() {
//    val input =
//      """
//      2023-06-28
//
//      * Bug fixes
//      * New features
//
//      2023-06-27
//
//      * Other changes
//      """
//        .trimIndent()
//    val expectedDate = LocalDate.of(2023, 6, 28)
//    val expectedString =
//      """
//      2023-06-28
//
//      * Bug fixes
//      * New features
//      """
//        .trimIndent()
//    val (changeLogString, latestEntry) = ChangelogParser.readFile(input, null)
//    assertThat(changeLogString).isEqualTo(expectedString)
//    assertThat(latestEntry).isEqualTo(expectedDate)
//  }
//
//  @Test
//  fun `multiple entries, where the previous is the same as the latest`() {
//    val input =
//      """
//      2023-06-28
//
//      * Bug fixes
//      * New features
//
//      2023-06-27
//
//      * Other changes
//      """
//        .trimIndent()
//    val expectedDate = LocalDate.of(2023, 6, 28)
//    val (changeLogString, latestEntry) = ChangelogParser.readFile(input, LocalDate.of(2023, 6,
// 28))
//    assertThat(changeLogString).isNull()
//    assertThat(latestEntry).isEqualTo(expectedDate)
//  }
//
//  @Test
//  fun `test with a previous entry not in the change log`() {
//    val input =
//      """
//      2023-06-28
//
//      * Bug fixes
//      * New features
//
//      2023-06-27
//
//      * Other changes
//      """
//        .trimIndent()
//    val expectedDate = LocalDate.of(2023, 6, 28)
//    val expectedString =
//      """
//      2023-06-28
//
//      * Bug fixes
//      * New features
//
//      2023-06-27
//
//      * Other changes
//      """
//        .trimIndent()
//    val (changeLogString, latestEntry) = ChangelogParser.readFile(input, LocalDate.of(2023, 6,
// 29))
//    assertThat(changeLogString).isEqualTo(expectedString)
//    assertThat(latestEntry).isEqualTo(expectedDate)
//  }
//
//  @Test
//  fun `multiple entries, previous entry matches but not the latest`() {
//    val input =
//      """
//        2023-06-30
//
//        * Even more bug fixes
//
//        2023-06-29
//
//        * More bug fixes
//
//        2023-06-28
//
//        * Bug fixes
//        * New features
//
//        2023-06-27
//
//        * Other changes
//        """
//        .trimIndent()
//
//    val expectedDate = LocalDate.of(2023, 6, 30)
//    val expectedString =
//      """
//        2023-06-30
//
//        * Even more bug fixes
//
//        2023-06-29
//
//        * More bug fixes
//        """
//        .trimIndent()
//
//    val (changeLogString, latestEntry) = ChangelogParser.readFile(input, LocalDate.of(2023, 6,
// 28))
//
//    assertThat(changeLogString).isEqualTo(expectedString)
//    assertThat(latestEntry).isEqualTo(expectedDate)
//  }
// }
