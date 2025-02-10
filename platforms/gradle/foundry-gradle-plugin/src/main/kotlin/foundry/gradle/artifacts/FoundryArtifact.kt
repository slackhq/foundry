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

  data object SkippyUnitTests : FoundryArtifact("skippyUnitTests", "skippy") {
    private fun readResolve(): Any = SkippyUnitTests
  }

  data object SkippyRoborazziTests : FoundryArtifact("skippyRoborazziTests", "skippy") {
    private fun readResolve(): Any = SkippyRoborazziTests
  }

  data object SkippyEwTests : FoundryArtifact("skippyEwTests", "skippy") {
    private fun readResolve(): Any = SkippyEwTests
  }

  data object SkippyLint : FoundryArtifact("skippyLint", "skippy") {
    private fun readResolve(): Any = SkippyLint
  }

  data object SkippyAvoidedTasks : FoundryArtifact("skippyAvoidedTasks", "skippy") {
    private fun readResolve(): Any = SkippyAvoidedTasks
  }

  data object SkippyAndroidTestProject : FoundryArtifact("skippyAndroidTestProject", "skippy") {
    private fun readResolve(): Any = SkippyAndroidTestProject
  }

  data object SkippyDetekt : FoundryArtifact("skippyDetekt", "skippy") {
    private fun readResolve(): Any = SkippyDetekt
  }

  data object SkippyValidateTopography : FoundryArtifact("skippyValidateTopography", "skippy") {
    private fun readResolve(): Any = SkippyValidateTopography
  }

  data object AndroidTestApkDirs : FoundryArtifact("androidTestApkDirs", "androidTest") {
    private fun readResolve(): Any = AndroidTestApkDirs
  }

  data object DagpMissingIdentifiers : FoundryArtifact("dagpMissingIdentifiers", "dependencyRake") {
    private fun readResolve(): Any = DagpMissingIdentifiers
  }

  data object ModStatsFiles : FoundryArtifact("modStatsFiles", "modscore") {
    private fun readResolve(): Any = ModStatsFiles
  }
}
