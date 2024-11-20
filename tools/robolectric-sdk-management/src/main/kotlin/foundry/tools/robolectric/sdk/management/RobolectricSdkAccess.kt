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
package foundry.tools.robolectric.sdk.management

import java.net.URL
import org.robolectric.internal.dependency.DependencyJar
import org.robolectric.internal.dependency.DependencyResolver
import org.robolectric.plugins.DefaultSdkProvider

/** A helper for reading available SDK jars from Robolectric's runtime artifact of SDK data. */
public object RobolectricSdkAccess {
  public fun loadSdks(apis: Collection<Int>): List<String> {
    val sdks =
      DefaultSdkProvider(EMPTY_RESOLVER)
        .sdks
        .filterIsInstance<DefaultSdkProvider.DefaultSdk>()
        .associateBy { it.apiLevel }
    return apis.map { sdkInt ->
      val sdk = sdks[sdkInt] ?: error("No robolectric jar coordinates found for $sdkInt.")
      // The full dep
      buildString {
        append("org.robolectric:android-all-instrumented:")
        append(sdk.androidVersion)
        append("-robolectric-")
        append(ROBO_VERSION_FIELD.get(sdk) as Int)
        append("-i")
        I_VERSION
      }
    }
  }

  private val EMPTY_RESOLVER =
    object : DependencyResolver {
      override fun getLocalArtifactUrl(dependency: DependencyJar?): URL? {
        error("This should never be called in this context")
      }
    }

  private val I_VERSION =
    DefaultSdkProvider::class
      .java
      .getDeclaredField("PREINSTRUMENTED_VERSION")
      .apply { isAccessible = true }
      .get(null) as Int

  private val ROBO_VERSION_FIELD =
    DefaultSdkProvider.DefaultSdk::class.java.getField("robolectricVersion").apply {
      isAccessible = true
    }
}
