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
package foundry.gradle

import com.google.common.truth.Truth.assertThat
import foundry.gradle.Configurations.isPlatformConfigurationName
import foundry.gradle.Configurations.shouldDetachAndroidTestDependencies
import org.junit.Test

class StandardProjectConfigurationsTest {

  @Test
  fun detachAndroidTestDependenciesWhenDisabled() {
    // androidTest configs are detached only when the feature is not opted in, so AGP has no
    // inherited dependencies to warn about on the disabled variant.
    for (name in listOf("androidTestImplementation", "androidTestApi", "androidTestUtil")) {
      assertThat(shouldDetachAndroidTestDependencies(name, androidTestEnabled = false)).isTrue()
      assertThat(shouldDetachAndroidTestDependencies(name, androidTestEnabled = true)).isFalse()
    }
  }

  @Test
  fun neverDetachNonAndroidTestDependencies() {
    // Unit test and main configs keep their inheritance regardless of the androidTest flag.
    for (name in listOf("implementation", "api", "testImplementation", "testApi")) {
      assertThat(shouldDetachAndroidTestDependencies(name, androidTestEnabled = false)).isFalse()
      assertThat(shouldDetachAndroidTestDependencies(name, androidTestEnabled = true)).isFalse()
    }
  }

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
