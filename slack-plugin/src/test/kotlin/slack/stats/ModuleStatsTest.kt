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
import org.junit.Test

class ModuleStatsTest {
  @Test
  fun parseProjectDeps() {
    // language=Kotlin
    val buildFileText =
      """
      import slack.gradle.dependencies.SlackDependencies

      plugins {
        kotlin("jvm")
        `java-test-fixtures`
        id("net.ltgt.errorprone")
      }

      slack {
        features {
          moshi(codegen = false, adapters = true)
        }
      }

      dependencies {
        compileOnly(SlackDependencies.Androidx.annotation)
        compileOnly(projects.libraries.foundation.jsr305)
        implementation(SlackDependencies.Slack.eithernet)
        testFixturesApi(projects.libraries.foundation.guinness.testUtils)

        testImplementation(SlackDependencies.Testing.truth)
        testImplementation(projects.libraries.foundation.testCommons)
      }

      """.trimIndent()

    val deps = StatsUtils.parseProjectDeps(buildFileText)
    assertThat(deps)
      .containsExactly("libraries.foundation.jsr305", "libraries.foundation.testCommons")
  }
}
