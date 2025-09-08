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
// PostStartupActivityExtension.kt (Android Studio only)
package foundry.intellij.skate

import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

internal class PostStartupActivityExtension : ProjectActivity {

  override suspend fun execute(project: Project) {
    val dumb = DumbService.getInstance(project)

    // 1) Show once after startup, when indices are ready (deterministic)
    dumb.runWhenSmart {
      if (!project.isDisposed) {
        project.service<SkateProjectService>().showWhatsNewPanel()
      }
    }

    // 2) Also show after a successful Gradle sync (Android Studio only)
    GradleSyncState.subscribe(
      project,
      object : GradleSyncListener {
        override fun syncStarted(project: Project) = Unit

        override fun syncSucceeded(project: Project) {
          // Schedule on smart mode to avoid races with indexing / EDT modality
          DumbService.getInstance(project).smartInvokeLater {
            if (!project.isDisposed) {
              project.service<SkateProjectService>().showWhatsNewPanel()
            }
          }
        }

        override fun syncSkipped(project: Project) = Unit
        override fun syncFailed(project: Project, errorMessage: String) {}
      },
      /* disposable = */ project
    )
  }
}
