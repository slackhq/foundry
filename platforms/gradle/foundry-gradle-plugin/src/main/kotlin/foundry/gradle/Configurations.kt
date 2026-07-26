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

internal object Configurations {

  // The "(?i)" makes the regex case-insensitive
  private val testConfigurationRegex = Regex("(?i).*?([a-z]*Test|androidUnitTest).*")

  const val COMPILE_ONLY = "compileOnly"
  const val CORE_LIBRARY_DESUGARING = "coreLibraryDesugaring"
  const val KAPT = "kapt"
  const val KSP = "ksp"
  const val SQL_DELIGHT_DIALECT = "DialectClasspath"

  fun isTest(name: String): Boolean = testConfigurationRegex.matches(name)

  /**
   * Whether [name] is an android instrumentation test ("androidTest") configuration, such as
   * `androidTestImplementation` or `androidTestUtil`. Note this intentionally does not match unit
   * test configurations like `testImplementation`.
   */
  fun isAndroidTest(name: String): Boolean = name.contains("androidTest", ignoreCase = true)

  fun isApi(name: String): Boolean = name.endsWith("api", ignoreCase = true)

  /**
   * Whether the androidTest configuration named [name] should be detached from its parent
   * configurations (i.e. have its `extendsFrom` cleared).
   *
   * When a module hasn't opted into androidTest ([androidTestEnabled] is false), AGP disables the
   * androidTest variant and builds nothing from its configurations. Those configurations still
   * extend `implementation`/`api` though, so AGP reports every inherited dependency as ignored
   * ("androidTestImplementation dependencies are ignored because androidTest is disabled").
   * Detaching them leaves nothing to ignore. Opted-in modules keep the normal inheritance.
   */
  fun shouldDetachAndroidTestDependencies(name: String, androidTestEnabled: Boolean): Boolean =
    isAndroidTest(name) && !androidTestEnabled

  object ErrorProne {
    const val ERROR_PRONE = "errorprone"
    const val ERROR_PRONE_JAVAC = "errorproneJavac"
  }

  object Groups {
    val APT: Set<String> = setOf(KAPT, "annotationProcessor")
    val ERROR_PRONE: Set<String> = setOf(ErrorProne.ERROR_PRONE, ErrorProne.ERROR_PRONE_JAVAC)

    /** Configurations that never run on Android and are also not visible to implementation. */
    val JRE: Set<String> = ERROR_PRONE + APT
    val RUNTIME: Set<String> = setOf("api", "compile", "implementation", "runtimeOnly")

    @Suppress("SpreadOperator")
    val PLATFORM =
      setOf(
        *APT.toTypedArray(),
        *ERROR_PRONE.toTypedArray(),
        *RUNTIME.toTypedArray(),
        COMPILE_ONLY,
        SQL_DELIGHT_DIALECT,
        "androidTestUtil",
        "lintChecks",
        "lintRelease",
      )
  }

  internal fun isKnownConfiguration(configurationName: String, knownNames: Set<String>): Boolean {
    // Try trimming the flavor by just matching the suffix
    return knownNames.any { platformConfig ->
      configurationName.endsWith(platformConfig, ignoreCase = true)
    }
  }

  /**
   * Best effort fuzzy matching on known configuration names that we want to opt into platforming.
   * We don't blanket apply them to all configurations because
   */
  internal fun isPlatformConfigurationName(name: String): Boolean {
    // Kapt/ksp/compileOnly are special cases since they can be combined with others
    val isKaptOrCompileOnly =
      name.startsWith(KAPT, ignoreCase = true) ||
        name.startsWith(KSP, ignoreCase = true) ||
        name == COMPILE_ONLY
    if (isKaptOrCompileOnly) {
      return true
    }
    return isKnownConfiguration(name, Groups.PLATFORM)
  }
}
