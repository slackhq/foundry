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
package foundry.gradle.util

import com.google.devtools.ksp.gradle.KspTask
import org.gradle.api.tasks.TaskContainer
import org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Configures [KotlinCompile] tasks with the given [action] but _ignores_ [KaptGenerateStubsTask]
 * and [KspTask] types as they inherit arguments from standard compilation tasks via and applying
 * arguments to them could result in duplicates.
 *
 * See
 * https://github.com/JetBrains/kotlin/blob/0e4e53786c1b0341befe8e71a5e6e0bc0e464370/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/internal/kapt/KaptGenerateStubsTask.kt#L111-L113
 */
internal fun TaskContainer.configureKotlinCompilationTask(
  includeKaptGenerateStubsTask: Boolean = false,
  includeKspTask: Boolean = false,
  action: KotlinCompilationTask<*>.() -> Unit,
) {
  withType(KotlinCompilationTask::class.java).configureEach {
    // Kapt stub gen is a special case because KGP sets it up to copy compiler args from the
    // standard kotlin compilation, which can lead to duplicates. SOOOO we skip configuration of
    // it here. Callers to this _can_ opt in to including it, but they must be explicit.
    if (includeKaptGenerateStubsTask || this !is KaptGenerateStubsTask) {
      if (includeKspTask || this !is KspTask) {
        action()
      }
    }
  }
}
