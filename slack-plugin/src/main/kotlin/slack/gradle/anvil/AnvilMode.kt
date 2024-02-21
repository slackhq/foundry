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
package slack.gradle.anvil

public enum class AnvilMode(
  public val useKspFactoryGen: Boolean,
  public val useKspComponentGen: Boolean,
  public val languageVersionOverride: String?,
) {
  /**
   * Anvil support for K1.
   * - `AnalysisHandlerExtension`
   * - KAPT3 component gen
   */
  K1(false, false, null),
  /**
   * Anvil support for K2. Same as [K1] but it forces Anvil-integrated kotlin compilations to
   * language version 1.9.
   */
  K2_COMPAT(false, false, "1.9"),
  /**
   * Anvil support for K2.
   * - KSP factory gen
   * - KAPT4 component gen
   */
  K2_KAPT(true, false, null),
  /**
   * Anvil support for K2.
   * - KSP factory gen
   * - KSP component gen
   */
  K2_KSP(true, true, null),
}
