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

internal object Configurations {
  const val COMPILE_ONLY = "compileOnly"
  const val CORE_LIBRARY_DESUGARING = "coreLibraryDesugaring"
  const val KAPT = "kapt"
  const val KSP = "ksp"

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
        CORE_LIBRARY_DESUGARING,
        "androidTestUtil",
        "lintChecks",
        "lintRelease"
      )
  }
}
