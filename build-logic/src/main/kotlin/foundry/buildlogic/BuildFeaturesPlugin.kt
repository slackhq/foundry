/*
 * Copyright (C) 2026 Slack Technologies, LLC
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
package foundry.buildlogic

import javax.inject.Inject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.configuration.BuildFeatures
import org.gradle.api.provider.Provider

/** Effective Gradle build features that can be consumed from Kotlin build scripts. */
public class BuildFeaturesExtension(public val isolatedProjects: Provider<Boolean>)

/** Makes Gradle's injected [BuildFeatures] available through [BuildFeaturesExtension]. */
public class BuildFeaturesPlugin @Inject constructor(buildFeatures: BuildFeatures) :
  Plugin<Project> {
  private val isolatedProjects = buildFeatures.isolatedProjects.active

  override fun apply(target: Project) {
    target.extensions.add("foundryBuildFeatures", BuildFeaturesExtension(isolatedProjects))
  }
}
