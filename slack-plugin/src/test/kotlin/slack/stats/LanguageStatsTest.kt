/*
 * Copyright (C) 2022 Slack Technologies, LLC
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
package slack.stats

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LanguageStatsTest {
  @JvmField @Rule val tmpFolder = TemporaryFolder()

  @Test
  fun basicJava() {
    val source = tmpFolder.newFile("Test.java")
    // language=Java
    source.writeText(
      """
      package test;

      /*

       */

      /**
       * Here is a multi-line doc
       *
       */
      class MyClass {
        /* Inline comment */
        /* Inline comment with code */ private int foo = 0;

        public MyClass {
          int bar = foo;
          System.out.println("Hello world!");

        }
      }
      """
        .trimIndent()
    )

    val languageStats = LocTask.processJvmFile(source) { println(it) }
    assertThat(languageStats).isEqualTo(LanguageStats(files = 1, code = 8, comment = 8, blank = 4))
  }

  @Test
  fun basicKotlin() {
    val source = tmpFolder.newFile("Test.kt")
    // language=kotlin
    source.writeText(
      """
      package test

      /*

       */

      /**
       * Here is a multi-line doc
       *
       */
      class MyClass {
        /* Inline comment */
        /* Inline comment with code */ private val foo = 0

        init {
          val bar = foo
          println("Hello world!")

        }
      }
      """
        .trimIndent()
    )

    val languageStats = LocTask.processJvmFile(source) { println(it) }
    assertThat(languageStats).isEqualTo(LanguageStats(files = 1, code = 8, comment = 8, blank = 4))
  }

  @Test
  fun basicXml() {
    val source = tmpFolder.newFile("Test.xml")
    // language=XML
    source.writeText(
      """
      <?xml version="1.0" encoding="utf-8"?>

      <!-- Single line comment -->

      <!--
       Multiline
       comment

       -->

      <FrameLayout
          xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:tools="http://schemas.android.com/tools"
          android:layout_width="wrap_content"
          android:layout_height="wrap_content"
          android:layout_marginEnd="@dimen/sk_spacing_50">

          <!-- Single line comment --> <androidx.constraintlayout.utils.widget.ImageFilterView
              android:id="@+id/thumbnail"
              android:layout_margin="@dimen/story_list_thumbnail_margin"
              android:layout_width="@dimen/story_list_thumbnail_size"
              android:layout_height="@dimen/story_list_thumbnail_size"
              tools:background="@color/sk_lilypad_green"/>

          <View
              android:id="@+id/border_unread"
              android:layout_width="@dimen/story_list_item_size"
              android:layout_height="@dimen/story_list_item_size"
              tools:background="@drawable/unread_border_1"/>

      </FrameLayout>
      """
        .trimIndent()
    )

    val languageStats = LocTask.processXmlFile(source) { println(it) }
    assertThat(languageStats).isEqualTo(LanguageStats(files = 1, code = 19, comment = 6, blank = 6))
  }

  @Test
  fun merge() {
    val first =
      mapOf(
        "Kotlin" to LanguageStats(files = 1, code = 4, comment = 5, blank = 4),
        "Java" to LanguageStats(files = 1, code = 4, comment = 5, blank = 4)
      )
    val second = mapOf("Kotlin" to LanguageStats(files = 1, code = 4, comment = 5, blank = 4))

    val final = first.mergeWith(second)
    assertThat(final)
      .containsExactlyEntriesIn(
        mapOf(
          "Kotlin" to LanguageStats(files = 2, code = 8, comment = 10, blank = 8),
          "Java" to LanguageStats(files = 1, code = 4, comment = 5, blank = 4)
        )
      )
  }
}
