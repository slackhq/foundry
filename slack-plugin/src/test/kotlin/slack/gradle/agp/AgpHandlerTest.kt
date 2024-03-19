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
package slack.gradle.agp

import com.android.build.api.AndroidPluginVersion
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AgpHandlerTest {

  @Test
  fun `alpha version when version is alpha should be used`() {
    val agp81alpha = AndroidPluginVersion(8, 1, 0).alpha(5)
    val agp81alphaFactory = newFactory(agp81alpha)
    val agp80 = AndroidPluginVersion(8, 0, 0)
    val agp80Factory = newFactory(agp80)
    val resolved =
      AgpHandlers.resolveFactory(
        factories = sequenceOf(agp80Factory, agp81alphaFactory),
        testAgpVersion = agp81alpha,
      )
    assertThat(resolved).isSameInstanceAs(agp81alphaFactory)
  }

  @Test
  fun `stable version when version is stable should be used`() {
    val agp81alpha = AndroidPluginVersion(8, 1, 0).alpha(5)
    val agp81alphaFactory = newFactory(agp81alpha)
    val agp80 = AndroidPluginVersion(8, 0, 0)
    val agp80Factory = newFactory(agp80)
    val resolved =
      AgpHandlers.resolveFactory(
        factories = sequenceOf(agp80Factory, agp81alphaFactory),
        testAgpVersion = agp80,
      )
    assertThat(resolved).isSameInstanceAs(agp80Factory)
  }

  private fun newFactory(version: AndroidPluginVersion): AgpHandler.Factory {
    return object : AgpHandler.Factory {
      override val minVersion: AndroidPluginVersion
        get() = version

      override val currentVersion: AndroidPluginVersion
        get() = version

      override fun create(): AgpHandler {
        throw NotImplementedError()
      }

      override fun equals(other: Any?): Boolean {
        if (other !is AgpHandler.Factory) return false
        return minVersion == other.minVersion
      }

      override fun hashCode() = version.hashCode()

      override fun toString() = "AgpHandlerFactory($minVersion)"
    }
  }
}
