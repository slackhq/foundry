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
package foundry.gradle.artifacts

import org.gradle.api.attributes.Attribute

internal sealed class FoundryArtifact(override val declarableName: String, val category: String) :
  ShareableArtifact<FoundryArtifact> {
  final override val attribute: Attribute<FoundryArtifact>
    get() = FOUNDRY_ARTIFACTS_ATTRIBUTE

  companion object {
    @JvmField
    val FOUNDRY_ARTIFACTS_ATTRIBUTE: Attribute<FoundryArtifact> =
      Attribute.of("foundry.internal.artifacts", FoundryArtifact::class.java)
  }

  data object SKIPPY_UNIT_TESTS : FoundryArtifact("skippyUnitTests", "skippy") {
    private fun readResolve(): Any = SKIPPY_UNIT_TESTS
  }

  data object SKIPPY_LINT : FoundryArtifact("skippyLint", "skippy") {
    private fun readResolve(): Any = SKIPPY_LINT
  }

  data object SKIPPY_AVOIDED_TASKS : FoundryArtifact("skippyAvoidedTasks", "skippy") {
    private fun readResolve(): Any = SKIPPY_AVOIDED_TASKS
  }

  data object SKIPPY_ANDROID_TEST_PROJECT : FoundryArtifact("skippyAndroidTestProject", "skippy") {
    private fun readResolve(): Any = SKIPPY_ANDROID_TEST_PROJECT
  }

  data object SKIPPY_DETEKT : FoundryArtifact("skippyDetekt", "skippy") {
    private fun readResolve(): Any = SKIPPY_DETEKT
  }

  data object SKIPPY_VALIDATE_TOPOGRAPHY : FoundryArtifact("skippyValidateTopography", "skippy") {
    private fun readResolve(): Any = SKIPPY_VALIDATE_TOPOGRAPHY
  }

  data object ANDROID_TEST_APK_DIRS : FoundryArtifact("androidTestApkDirs", "androidTest") {
    private fun readResolve(): Any = ANDROID_TEST_APK_DIRS
  }

  data object DAGP_MISSING_IDENTIFIERS :
    FoundryArtifact("dagpMissingIdentifiers", "dependencyRake") {
    private fun readResolve(): Any = DAGP_MISSING_IDENTIFIERS
  }

  data object MOD_STATS_STATS_FILES : FoundryArtifact("modStatsFiles", "modscore") {
    private fun readResolve(): Any = MOD_STATS_STATS_FILES
  }
}
