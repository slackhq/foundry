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
package foundry.gradle.avoidance

import foundry.gradle.FoundryExtensionMarker
import foundry.gradle.FoundryProperties
import foundry.gradle.property
import foundry.gradle.setProperty
import foundry.skippy.AffectedProjectsDefaults.DEFAULT_INCLUDE_PATTERNS
import foundry.skippy.AffectedProjectsDefaults.DEFAULT_NEVER_SKIP_PATTERNS
import foundry.skippy.SkippyConfig
import foundry.skippy.SkippyConfig.Companion.GLOBAL_TOOL
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input

@FoundryExtensionMarker
public abstract class SkippyExtension
@Inject
constructor(foundryProperties: FoundryProperties, objects: ObjectFactory) {

  public val debug: Property<Boolean> =
    objects.property<Boolean>().convention(foundryProperties.debug)

  public val mergeOutputs: Property<Boolean> = objects.property<Boolean>().convention(true)

  public val computeInParallel: Property<Boolean> = objects.property<Boolean>().convention(true)

  internal val configs: NamedDomainObjectContainer<SkippyGradleConfig> =
    objects.domainObjectContainer(SkippyGradleConfig::class.java)

  init {
    // One global default
    configs.maybeCreate(GLOBAL_TOOL)
  }

  public fun global(action: Action<SkippyGradleConfig>) {
    action.execute(configs.maybeCreate(GLOBAL_TOOL))
  }

  public fun config(name: String, action: Action<SkippyGradleConfig>) {
    action.execute(configs.maybeCreate(name))
  }
}

/** A gradle representation of [SkippyConfig]. See its doc for more details. */
public abstract class SkippyGradleConfig
@Inject
constructor(private val name: String, objects: ObjectFactory) : Named {
  override fun getName(): String = name

  @get:Input
  public val includePatterns: SetProperty<String> =
    objects.setProperty<String>().convention(DEFAULT_INCLUDE_PATTERNS)

  @get:Input
  public val excludePatterns: SetProperty<String> =
    objects.setProperty<String>().convention(emptySet())

  @get:Input
  public val neverSkipPatterns: SetProperty<String> =
    objects.setProperty<String>().convention(DEFAULT_NEVER_SKIP_PATTERNS)

  /**
   * Apply the default patterns to this config. This is useful when adding to them but wanting to
   * keep the defaults applied too.
   */
  public fun applyDefaults() {
    includePatterns.addAll(DEFAULT_INCLUDE_PATTERNS)
    neverSkipPatterns.addAll(DEFAULT_NEVER_SKIP_PATTERNS)
  }

  internal fun asSkippyConfig(): SkippyConfig {
    return SkippyConfig(
      tool = name,
      buildUponDefaults = false,
      _includePatterns = includePatterns.get(),
      _excludePatterns = excludePatterns.get(),
      _neverSkipPatterns = neverSkipPatterns.get(),
    )
  }
}
