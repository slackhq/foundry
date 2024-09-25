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

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

@Suppress("TopLevelPropertyNaming") // Why is this a problem in detect? It's a static final constant
private val UNINITIALIZED_VALUE = Any()

internal infix fun DependencyDelegate.withComments(comments: String?): DependencyDelegate {
  return copy(comments = comments)
}

internal infix fun DependencyDelegate.noBom(comments: String?): DependencyDelegate {
  return copy(isBomManaged = false, comments = comments)
}

internal data class DependencyDelegate(
  private val group: String,
  private val artifact: String?,
  private val comments: String? = null,
  private val ext: String? = null,
  private val gradleProperty: String? = null,
  private val isBomManaged: Boolean = false,
) : ReadOnlyProperty<DependencyCollection, Any> {

  private var _definitionRef: Any = UNINITIALIZED_VALUE

  override fun getValue(thisRef: DependencyCollection, property: KProperty<*>): Any {
    return getOrCreateDef(property).coordinates
  }

  @Synchronized
  fun getOrCreateDef(property: KProperty<*>): DependencyDef {
    if (_definitionRef === UNINITIALIZED_VALUE) {
      val resolvedArtifact = artifact ?: property.name
      val gradleProperty = gradleProperty ?: computeVersionGradleProperty(resolvedArtifact)
      _definitionRef =
        DependencyDef(
          group = group,
          artifact = resolvedArtifact,
          comments = comments,
          ext = ext,
          gradleProperty = gradleProperty,
          isBomManaged = isBomManaged,
        )
    }
    return _definitionRef as DependencyDef
  }

  private fun computeVersionGradleProperty(resolvedArtifact: String): String {
    return "${DependencyCollection.GRADLE_PROPERTY_PREFIX}$resolvedArtifact"
  }
}
