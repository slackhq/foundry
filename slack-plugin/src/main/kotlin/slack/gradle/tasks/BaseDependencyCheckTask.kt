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

import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier

public abstract class BaseDependencyCheckTask : DefaultTask() {
  @get:Classpath
  @get:InputFiles
  public abstract val resolvedArtifacts: SetProperty<ResolvedArtifactResult>

  internal abstract fun handleDependencies(dependencies: Map<String, String>)

  @TaskAction
  internal fun check() {
    val components =
      resolvedArtifacts
        .get()
        .map { it.id }
        .filterIsInstance<ModuleComponentArtifactIdentifier>()
        .associate { component ->
          val componentId = component.componentIdentifier
          val identifier = "${componentId.group}:${componentId.module}"
          identifier to componentId.version
        }

    handleDependencies(components)
  }

  protected companion object {
    internal fun Configuration.classesArtifacts(objects: ObjectFactory): ArtifactCollection {
      return incoming
        .artifactView {
          attributes {
            attribute(USAGE_ATTRIBUTE, objects.named(Usage::class.java, ArtifactType.CLASSES.type))
          }
          lenient(true)
        }
        .artifacts
    }
  }
}
