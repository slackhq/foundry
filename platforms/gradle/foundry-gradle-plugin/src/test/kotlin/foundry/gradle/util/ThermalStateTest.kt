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
package foundry.gradle.util

import com.google.common.truth.Truth.assertThat
import foundry.gradle.util.AppleSiliconThermals.ThermalState
import org.junit.Test

class ThermalStateTest {

  @Test
  fun knownRawValuesMapToEntries() {
    assertThat(ThermalState.fromRawValue(0)).isEqualTo(ThermalState.NOMINAL)
    assertThat(ThermalState.fromRawValue(1)).isEqualTo(ThermalState.FAIR)
    assertThat(ThermalState.fromRawValue(2)).isEqualTo(ThermalState.SERIOUS)
    assertThat(ThermalState.fromRawValue(3)).isEqualTo(ThermalState.CRITICAL)
  }

  @Test
  fun unknownRawValuesMapToNull() {
    assertThat(ThermalState.fromRawValue(-1)).isNull()
    assertThat(ThermalState.fromRawValue(4)).isNull()
  }
}
