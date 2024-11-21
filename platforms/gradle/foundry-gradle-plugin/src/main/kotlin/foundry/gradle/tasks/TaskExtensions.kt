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

import app.cash.sqldelight.gradle.GenerateSchemaTask
import app.cash.sqldelight.gradle.SqlDelightTask
import com.android.build.gradle.internal.tasks.databinding.DataBindingGenBaseClassesTask
import com.google.devtools.ksp.gradle.KspAATask
import com.google.devtools.ksp.gradle.KspTask
import com.squareup.wire.gradle.WireTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.internal.KaptTask
import org.jetbrains.kotlin.gradle.tasks.KaptGenerateStubs
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Makes this task depend on commonly known source-generating tasks.
 *
 * @param includeCompilerTasks some compiler tasks like javac and kotlinc can produce new source
 *   files too, namely during annotation processing.
 */
internal fun TaskProvider<*>.mustRunAfterSourceGeneratingTasks(
  project: Project,
  includeCompilerTasks: Boolean,
) {
  // Kapt
  project.pluginManager.withPlugin("org.jetbrains.kotlin.kapt") {
    configure {
      mustRunAfter(project.tasks.withType(KaptGenerateStubs::class.java))
      mustRunAfter(project.tasks.withType(KaptTask::class.java))
    }
  }
  // KSP
  project.pluginManager.withPlugin("com.google.devtools.ksp") {
    configure {
      mustRunAfter(project.tasks.withType(KspTask::class.java))
      mustRunAfter(project.tasks.withType(KspAATask::class.java))
    }
  }
  // ViewBinding
  project.pluginManager.withPlugin("com.android.base") {
    configure { mustRunAfter(project.tasks.withType(DataBindingGenBaseClassesTask::class.java)) }
  }
  // SqlDelight
  project.pluginManager.withPlugin("app.cash.sqldelight") {
    configure {
      mustRunAfter(project.tasks.withType(SqlDelightTask::class.java))
      mustRunAfter(project.tasks.withType(GenerateSchemaTask::class.java))
    }
  }
  // Wire
  project.pluginManager.withPlugin("com.squareup.wire") {
    configure { mustRunAfter(project.tasks.withType(WireTask::class.java)) }
  }

  if (includeCompilerTasks) {
    project.pluginManager.withPlugin("org.jetbrains.kotlin.base") {
      configure { mustRunAfter(project.tasks.withType(KotlinCompile::class.java)) }
      // KGP may create JavaCompile tasks
      configure { mustRunAfter(project.tasks.withType(JavaCompile::class.java)) }
    }
    project.pluginManager.withPlugin("java") {
      configure { mustRunAfter(project.tasks.withType(JavaCompile::class.java)) }
    }
  }
}
