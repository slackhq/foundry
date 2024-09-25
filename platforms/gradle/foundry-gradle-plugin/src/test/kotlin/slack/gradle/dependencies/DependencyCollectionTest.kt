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
package slack.gradle.dependencies

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DependencyCollectionTest {

  @Test
  fun flattened() {
    val flattened = TestDependencies.flattenedPlatformCoordinates()
    assertThat(flattened)
      .containsExactlyElementsIn(
        setOf(
          DependencyDef(
            group = "com.davemorrissey.labs",
            artifact = "subsampling-scale-image-view-androidx",
            gradleProperty = "slack.dependencies.subsampling-scale-image-view-androidx",
          ),
          DependencyDef(
            group = "com.example",
            artifact = "something",
            gradleProperty = "slack.dependencies.somethingelse",
          ),
          DependencyDef(
            group = "com.example.labs",
            artifact = "another-example",
            gradleProperty = "slack.dependencies.another-example",
          ),
          DependencyDef(
            group = "com.google.guava",
            artifact = "guava",
            gradleProperty = "slack.dependencies.pomegranate",
          ),
          DependencyDef(
            group = "com.google.guava",
            artifact = "listenablefuture",
            gradleProperty = "slack.dependencies.pomegranate",
          ),
          DependencyDef(
            group = "com.android.tools.lint",
            artifact = "lint",
            gradleProperty = "slack.dependencies.somethingelse",
          ),
          DependencyDef(
            group = "com.android.tools.lint",
            artifact = "lint-api",
            gradleProperty = "slack.dependencies.samplegroup",
          ),
          DependencyDef(
            group = "com.android.tools",
            artifact = "testutils",
            gradleProperty = "slack.dependencies.samplegroup",
          ),
          DependencyDef(
            group = "com.android.tools.build",
            artifact = "gradle",
            gradleProperty = "slack.dependencies.agp",
          ),
        )
      )
  }

  @Test
  fun identifiersToGradleProperties() {
    val mapping = TestDependencies.identifiersToGradleProperties(true)
    assertThat(mapping)
      .containsExactlyEntriesIn(
        mapOf(
          "com.davemorrissey.labs:subsampling-scale-image-view-androidx" to
            "slack.dependencies.subsampling-scale-image-view-androidx",
          "com.example:something" to "slack.dependencies.somethingelse",
          "com.example.labs:another-example" to "slack.dependencies.another-example",
          "com.google.guava:guava" to "slack.dependencies.pomegranate",
          "com.google.guava:listenablefuture" to "slack.dependencies.pomegranate",
          "com.android.tools.lint:lint" to "slack.dependencies.somethingelse",
          "com.android.tools.lint:lint-api" to "slack.dependencies.samplegroup",
          "com.android.tools:testutils" to "slack.dependencies.samplegroup",
          "com.android.tools.build:gradle" to "slack.dependencies.agp",
        )
      )
  }

  @Test
  fun identifierMap() {
    val mapping = TestDependencies.identifierMap()
    assertThat(mapping)
      .containsExactlyEntriesIn(
        mapOf(
          "com.davemorrissey.labs:subsampling-scale-image-view-androidx" to
            "TestDependencies.topLevelDep",
          "com.example:something" to "TestDependencies.topLevelDep2",
          "com.example.labs:another-example" to "TestDependencies.NestedSample.nestedProp",
          "com.google.guava:guava" to "TestDependencies.NestedSample.Guava.guava",
          "com.google.guava:listenablefuture" to
            "TestDependencies.NestedSample.Guava.listenablefuture",
          "com.android.tools.lint:lint" to "TestDependencies.SampleGroup.lint",
          "com.android.tools.lint:lint-api" to "TestDependencies.SampleGroup.lintApi",
          "com.android.tools:testutils" to "TestDependencies.SampleGroup.testUtils",
          "com.android.tools.build:gradle" to "TestDependencies.SampleSet.android",
        )
      )
  }

  @Suppress("unused")
  object TestDependencies : DependencySet() {
    val topLevelDep by artifact("com.davemorrissey.labs", "subsampling-scale-image-view-androidx")

    val topLevelDep2 by artifact("com.example", "something", gradleProperty = "somethingelse")

    object SkippedObject {
      const val ktlint = "0.36.0"
      const val kotlin = "1.3.61"
    }

    object SampleSet : DependencySet() {
      val android by artifact("com.android.tools.build", "gradle", gradleProperty = "agp")
    }

    object SampleGroup : DependencyGroup(group = "com.android.tools.lint") {
      val lintApi by artifact("lint-api")

      val lint by artifact(gradleProperty = "somethingelse")

      val testUtils by artifact(artifact = "testutils", groupOverride = "com.android.tools")
    }

    object NestedSample : DependencyCollection {
      val nestedProp by artifact("com.example.labs", "another-example")

      object Guava : DependencyGroup(group = "com.google.guava", gradleProperty = "pomegranate") {
        @Suppress("MemberNameEqualsClassName") val guava by artifact()

        val listenablefuture by artifact()
      }
    }
  }
}
