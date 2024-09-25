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
package slack.gradle.agp

import com.android.build.api.AndroidPluginVersion
import java.util.ServiceLoader

public object AgpHandlers {

  private fun loadFactories(): Sequence<AgpHandler.Factory> {
    return ServiceLoader.load(AgpHandler.Factory::class.java).asSequence() +
      AgpHandler.Factory.Default
  }

  /**
   * Load [factories][AgpHandler.Factory] and pick the highest compatible version (by
   * [AgpHandler.Factory.minVersion])
   */
  public fun resolveFactory(
    factories: Sequence<AgpHandler.Factory> = loadFactories(),
    testAgpVersion: AndroidPluginVersion? = null,
  ): AgpHandler.Factory {
    val targetFactory =
      factories
        .mapNotNull { factory ->
          // Filter out any factories that can't compute the AGP version, as
          // they're _definitely_ not compatible
          try {
            FactoryData(factory.currentVersion, factory)
          } catch (t: Throwable) {
            null
          }
        }
        .filter { (agpVersion, factory) -> (testAgpVersion ?: agpVersion) >= factory.minVersion }
        .maxByOrNull { (_, factory) -> factory.minVersion }
        ?.factory ?: error("Unrecognized AGP version!")
    return targetFactory
  }

  public fun createHandler(): AgpHandler = resolveFactory().create()
}

private data class FactoryData(
  val agpVersion: AndroidPluginVersion,
  val factory: AgpHandler.Factory,
)
