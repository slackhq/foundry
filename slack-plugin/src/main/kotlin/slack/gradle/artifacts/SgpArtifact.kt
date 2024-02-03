/*
 * Copyright (C) 2024 Slack Technologies, LLC
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
package slack.gradle.artifacts

import org.gradle.api.attributes.Attribute

internal sealed class SgpArtifact(override val declarableName: String, val category: String) :
  ShareableArtifact<SgpArtifact> {
  final override val attribute: Attribute<SgpArtifact>
    get() = SGP_ARTIFACTS_ATTRIBUTE

  companion object {
    @JvmField
    val SGP_ARTIFACTS_ATTRIBUTE: Attribute<SgpArtifact> =
      Attribute.of("sgp.internal.artifacts", SgpArtifact::class.java)
  }

  data object SKIPPY_UNIT_TESTS : SgpArtifact("skippyUnitTests", "skippy") {
    private fun readResolve(): Any = SKIPPY_UNIT_TESTS
  }

  data object SKIPPY_LINT : SgpArtifact("skippyLint", "skippy") {
    private fun readResolve(): Any = SKIPPY_LINT
  }

  data object SKIPPY_AVOIDED_TASKS : SgpArtifact("skippyAvoidedTasks", "skippy") {
    private fun readResolve(): Any = SKIPPY_AVOIDED_TASKS
  }

  data object SKIPPY_ANDROID_TEST_PROJECT : SgpArtifact("skippyAndroidTestProject", "skippy") {
    private fun readResolve(): Any = SKIPPY_ANDROID_TEST_PROJECT
  }

  data object SKIPPY_DETEKT : SgpArtifact("skippyDetekt", "skippy") {
    private fun readResolve(): Any = SKIPPY_DETEKT
  }

  data object ANDROID_TEST_APK_DIRS : SgpArtifact("androidTestApkDirs", "androidTest") {
    private fun readResolve(): Any = ANDROID_TEST_APK_DIRS
  }

  data object DAGP_MISSING_IDENTIFIERS : SgpArtifact("dagpMissingIdentifiers", "dependencyRake") {
    private fun readResolve(): Any = DAGP_MISSING_IDENTIFIERS
  }

  data object MOD_STATS_STATS_FILES : SgpArtifact("modStatsFiles", "modscore") {
    private fun readResolve(): Any = MOD_STATS_STATS_FILES
  }
}
