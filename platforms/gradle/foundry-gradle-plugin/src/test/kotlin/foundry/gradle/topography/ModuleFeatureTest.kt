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
package foundry.gradle.topography

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModuleFeatureTest {
  @Test
  fun overridesTest() {
    val customExplanation = "This is a custom explanation"
    val config =
      ModuleFeaturesConfig(
        _defaultFeatureOverrides =
          listOf(mapOf("name" to DefaultFeatures.Dagger.name, "explanation" to customExplanation))
      )

    val overriddenExplanation =
      config.loadFeatures().getValue(DefaultFeatures.Dagger.name).explanation
    assertThat(overriddenExplanation).isEqualTo(customExplanation)
  }
}
