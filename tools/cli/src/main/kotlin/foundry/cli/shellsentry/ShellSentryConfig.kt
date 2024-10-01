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
package foundry.cli.shellsentry

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlin.reflect.full.declaredMemberProperties

internal const val CURRENT_VERSION = 2

/** Represents a configuration for [ShellSentryCli]. */
@JsonClass(generateAdapter = true)
public data class ShellSentryConfig(
  val version: Int = CURRENT_VERSION,
  @Json(name = "gradle_enterprise_server") val gradleEnterpriseServer: String? = null,
  @Json(name = "known_issues")
  val knownIssues: List<Issue> =
    KnownIssues::class.declaredMemberProperties.map { it.get(KnownIssues) as Issue },
  /**
   * A minimum confidence level on a scale of [0-100] to accept. [AnalysisResult]s from
   * [ShellSentryExtension]s with lower confidence than this will be discarded.
   */
  @Json(name = "min_confidence") val minConfidence: Int = 75,
) {
  init {
    check(version == CURRENT_VERSION) {
      "Incompatible config version. Found $version, expected $CURRENT_VERSION."
    }
  }
}
