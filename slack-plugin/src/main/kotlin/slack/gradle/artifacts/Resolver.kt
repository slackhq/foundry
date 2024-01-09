/*
 * Copyright (C) 2024. Tony Robalik.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package slack.gradle.artifacts

import java.io.File
import java.io.Serializable
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
import org.gradle.api.provider.Provider

/**
 * Used for resolving custom artifacts in an aggregating project (often the "root" project), from
 * producing projects (often all or a subset of the subprojects ina build). Only for inter-project
 * publishing and resolving (e.g., _not_ for publishing to Artifactory). See also [Publisher].
 *
 * Represents a set of tightly coupled [Configuration]s:
 * * A "dependency scope" configuration ([declarable]).
 * * A "resolvable" configuration ([internal]).
 * * A "consumable" configuration ([Publisher.external]).
 *
 * Dependencies are _declared_ on [declarable], and resolved within a project via [internal]. Custom
 * artifacts (e.g., not jars), generated by tasks, are published via [Publisher.publish], which
 * should be used on dependency (artifact-producing) projects.
 *
 * Gradle uses [attributes][ShareableArtifact.attribute] to wire the consumer project's [internal]
 * (resolvable) configuration to the producer project's [Publisher.external] (consumable)
 * configuration, which is itself configured via [Publisher.publish].
 *
 * @see <a
 *   href="https://docs.gradle.org/current/userguide/cross_project_publications.html#sec:variant-aware-sharing">Variant-aware
 *   sharing of artifacts between projects</a>
 * @see <a
 *   href="https://dev.to/autonomousapps/configuration-roles-and-the-blogging-industrial-complex-21mn">Gradle
 *   configuration roles</a>
 * @see <a
 *   href="https://github.com/autonomousapps/dependency-analysis-gradle-plugin/blob/08c8765157925bbcdfd8f63d8d37fe041561ddb4/src/main/kotlin/com/autonomousapps/internal/artifacts/Resolver.kt">Resolver.kt</a>
 */
internal class Resolver<T : Serializable>(
  project: Project,
  private val attr: Attribute<T>,
  private val artifact: T,
  declarableName: String,
  category: String,
) {

  internal companion object {
    /**
     * Convenience function for creating a [Resolver] for inter-project resolving of [SgpArtifact].
     */
    fun interProjectResolver(
      project: Project,
      artifact: SgpArtifact,
      addDependencies: Boolean = true,
    ) =
      interProjectResolver(
        project,
        artifact.attribute,
        artifact,
        artifact.declarableName,
        artifact.category,
        addDependencies
      )

    fun <T : Serializable> interProjectResolver(
      project: Project,
      attr: Attribute<T>,
      artifact: T,
      declarableName: String,
      category: String,
      addDependencies: Boolean = true,
    ): Resolver<T> {
      project.logger.debug("Creating resolver for $artifact")
      val resolver = Resolver(project, attr, artifact, declarableName, category)
      if (addDependencies) {
        project.logger.debug("Adding subproject dependencies to $artifact via $declarableName")
        resolver.addSubprojectDependencies(project)
      }
      return resolver
    }
  }

  // Following the naming pattern established by the Java Library plugin. See
  // https://docs.gradle.org/current/userguide/java_library_plugin.html#sec:java_library_configurations_graph
  private val internalName = "${declarableName}Classpath"

  /** Dependencies are declared on this configuration */
  val declarable: Configuration = project.configurations.dependencyScope(declarableName).get()

  /**
   * The plugin will resolve dependencies against this internal configuration, which extends from
   * the declared dependencies.
   */
  val internal: NamedDomainObjectProvider<out Configuration> =
    project.configurations.resolvable(internalName) {
      extendsFrom(declarable)
      // This attribute is identical to what is set on the external/consumable configuration
      attributes {
        attribute(attr, artifact)
        addCommonAttributes(project, category)
      }
    }

  fun artifactView(): Provider<Set<File>> = artifactView(internal, attr, artifact)

  fun addSubprojectDependencies(project: Project) {
    project.dependencies.apply {
      for (subproject in project.subprojects) {
        // Ignore subprojects that don't have a build file. Gradle treats these as projects but we
        // don't.
        // Accessing the projectDir _should_ be project-isolation-safe according to
        // https://gradle.github.io/configuration-cache/#build_logic_constraints
        val projectDir = subproject.projectDir
        if (
          !File(projectDir, "build.gradle.kts").exists() &&
            !File(projectDir, "build.gradle").exists()
        ) {
          continue
        }
        add(declarable.name, project.project(subproject.path))
      }
    }
  }
}

// Extracted to a function to make it harder to accidentally capture non-serializable values
private fun <T : Serializable> artifactView(
  provider: NamedDomainObjectProvider<out Configuration>,
  attr: Attribute<T>,
  artifact: T
): Provider<Set<File>> {
  return provider.flatMap { configuration ->
    configuration.incoming
      .artifactView {
        // Enable lenient configuration to allow for missing artifacts, such as projects that
        // contribute nothing
        lenient(true)
        attributes { attribute(attr, artifact) }
      }
      .artifacts
      .resolvedArtifacts
      .map { resolvedArtifactResults ->
        resolvedArtifactResults.mapNotNullTo(mutableSetOf()) {
          // Inexplicably, Gradle sometimes gives us random files that don't match the attribute we
          // asked for. As a result, we need to add our own filter for the attribute.
          if (it.variant.attributes.getAttribute(attr) == artifact) {
            it.file
          } else {
            null
          }
        }
      }
  }
}
