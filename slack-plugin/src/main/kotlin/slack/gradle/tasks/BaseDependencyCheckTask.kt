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
package slack.gradle.tasks

import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier

public abstract class BaseDependencyCheckTask : DefaultTask() {
  @get:Input public abstract val identifiersToVersions: MapProperty<String, String>

  protected abstract fun handleDependencies(identifiersToVersions: Map<String, String>)

  protected fun configureIdentifiersToVersions(configuration: Configuration) {
    identifiersToVersions.putAll(
      configuration.incoming
        .artifactView {
          attributes { attribute(AndroidArtifacts.ARTIFACT_TYPE, ArtifactType.AAR_OR_JAR.type) }
          lenient(true)
          // Only resolve external dependencies! Without this, all project dependencies will get
          // _compiled_.
          componentFilter { id -> id is ModuleComponentIdentifier }
        }
        .artifacts
        .resolvedArtifacts
        // We _must_ map this here, can't defer to the task action because of
        // https://github.com/gradle/gradle/issues/20785
        .map { result ->
          result
            .asSequence()
            .map { it.id }
            .filterIsInstance<ModuleComponentArtifactIdentifier>()
            .associate { component ->
              val componentId = component.componentIdentifier
              val identifier = "${componentId.group}:${componentId.module}"
              identifier to componentId.version
            }
        }
    )
  }

  @TaskAction
  internal fun check() {
    handleDependencies(identifiersToVersions.get())
  }
}
