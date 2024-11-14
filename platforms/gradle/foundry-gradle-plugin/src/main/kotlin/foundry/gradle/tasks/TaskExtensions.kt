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
package foundry.gradle.tasks

import app.cash.sqldelight.gradle.SqlDelightTask
import com.android.build.gradle.internal.tasks.databinding.DataBindingGenBaseClassesTask
import com.google.devtools.ksp.gradle.KspAATask
import com.google.devtools.ksp.gradle.KspTask
import com.squareup.wire.gradle.WireTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.internal.KaptTask
import org.jetbrains.kotlin.gradle.tasks.KaptGenerateStubs

/** Makes this task depend on commonly known source-generating tasks. */
internal fun TaskProvider<*>.mustRunAfterSourceGeneratingTasks(project: Project) {
  // Kapt
  project.pluginManager.withPlugin("org.jetbrains.kotlin.kapt") {
    configure {
      dependsOn(project.tasks.withType(KaptGenerateStubs::class.java))
      dependsOn(project.tasks.withType(KaptTask::class.java))
    }
  }
  // KSP
  project.pluginManager.withPlugin("com.google.devtools.ksp") {
    configure {
      dependsOn(project.tasks.withType(KspTask::class.java))
      dependsOn(project.tasks.withType(KspAATask::class.java))
    }
  }
  // ViewBinding
  project.pluginManager.withPlugin("com.android.base") {
    configure { dependsOn(project.tasks.withType(DataBindingGenBaseClassesTask::class.java)) }
  }
  // SqlDelight
  project.pluginManager.withPlugin("app.cash.sqldelight") {
    configure { dependsOn(project.tasks.withType(SqlDelightTask::class.java)) }
  }
  // Wire
  project.pluginManager.withPlugin("com.squareup.wire") {
    configure { dependsOn(project.tasks.withType(WireTask::class.java)) }
  }
}
