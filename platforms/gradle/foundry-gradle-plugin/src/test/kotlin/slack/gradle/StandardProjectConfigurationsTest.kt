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
package slack.gradle

import org.junit.Test
import slack.gradle.Configurations.isPlatformConfigurationName

class StandardProjectConfigurationsTest {

  @Test
  fun platformConfigurations() {
    val validNames =
      setOf(
        "compileOnly",
        "kapt",
        "kaptTest",
        "kaptAndroidTest",
        "testCompileOnly",
        "implementation",
        "api",
        "testImplementation",
        "testApi",
        "androidTestImplementation",
        "androidTestApi",
        "kaptAndroidTest",
        "androidTestCompileOnly",
        "androidTestInternalDebugCompileOnly",
        "androidTestInternalDebugImplementation",
        "kaptInternalDebug",
      )

    for (name in validNames) {
      check(isPlatformConfigurationName(name)) {
        "Name is platform-compatible but isPlatformConfigurationName() returned false: '$name'"
      }
    }

    val invalidNames = setOf("runtime", "kotlinCompileClasspath", "runtimeClassPath")

    for (name in invalidNames) {
      check(!isPlatformConfigurationName(name)) {
        "Name is not platform-compatible but isPlatformConfigurationName() returned false: '$name'"
      }
    }
  }
}
