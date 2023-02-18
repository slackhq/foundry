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
package slack.gradle.util

import org.gradle.api.tasks.TaskContainer
import org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Configures [KotlinCompile] tasks with the given [action] but _ignores_ [KaptGenerateStubsTask]
 * types as they inherit arguments from standard tasks via
 * [KaptGenerateStubsTask.compileKotlinArgumentsContributor] and applying arguments to them could
 * result in duplicates.
 *
 * See
 * https://github.com/JetBrains/kotlin/blob/0e4e53786c1b0341befe8e71a5e6e0bc0e464370/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/internal/kapt/KaptGenerateStubsTask.kt#L111-L113
 */
internal fun TaskContainer.configureKotlinCompile(
  includeKaptGenerateStubsTask: Boolean = false,
  action: KotlinCompile.() -> Unit
) {
  withType(KotlinCompile::class.java)
    // Kapt stub gen is a special case because KGP sets it up to copy compiler args from the
    // standard kotlin compilation, which can lead to duplicates. SOOOO we skip configuration of
    // it here. Callers to this _can_ opt in to including it, but they must be explicit.
    .matching { includeKaptGenerateStubsTask || it !is KaptGenerateStubsTask }
    .configureEach { action() }
}
