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

import java.util.Locale

public abstract class DependencyGroup(
  internal val group: String,
  gradleProperty: String? = null,
  /**
   * Optional bom artifact that controls the versions of this group. If non-null, other dependencies
   * in this group will be controlled by this bom.
   */
  internal val bomArtifact: String? = null
) : DependencyCollection {

  /**
   * The final resolved gradle property to use. If one is provided via constructor, that's used. If
   * one isn't provided, the class simple name is used.
   *
   * ```
   * // This will be `slack.dependencies.retrofit`, derived from the class name "Retrofit"
   * object Retrofit : DependencyGroup("com.squareup.retrofit")
   *
   * // This will be slack.dependencies.customkey
   * object Retrofit : DependencyGroup("com.squareup.retrofit", "customkey")
   * ```
   */
  internal val groupGradleProperty by lazy {
    gradleProperty ?: this::class.simpleName!!.toLowerCase(Locale.US)
  }

  internal constructor(
    parent: DependencyGroup,
    groupSuffix: String = ""
  ) : this("${parent.group}$groupSuffix", parent.groupGradleProperty)

  internal fun artifact(
    artifact: String? = null,
    groupOverride: String = group,
    gradleProperty: String? = null
  ): DependencyDelegate {
    val property = gradleProperty ?: groupGradleProperty
    return DependencyDelegate(
      groupOverride,
      artifact,
      gradleProperty = "${DependencyCollection.GRADLE_PROPERTY_PREFIX}$property",
      isBomManaged = bomArtifact != null
    )
  }

  internal fun artifactWithExtension(
    artifact: String,
    ext: String,
    groupOverride: String = group,
    gradleProperty: String? = null
  ): DependencyDelegate {
    val property = gradleProperty ?: groupGradleProperty
    return DependencyDelegate(
      group = groupOverride,
      artifact = artifact,
      ext = ext,
      gradleProperty = "${DependencyCollection.GRADLE_PROPERTY_PREFIX}$property",
      isBomManaged = bomArtifact != null
    )
  }
}
