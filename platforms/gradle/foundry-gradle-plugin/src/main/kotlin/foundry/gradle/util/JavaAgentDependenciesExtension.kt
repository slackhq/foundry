/*
 * Copyright (C) 2025 Slack Technologies, LLC
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

import foundry.gradle.FoundryProperties
import foundry.gradle.newInstance
import javax.inject.Inject
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.tasks.testing.Test

/**
 * An extension that simplifies dependency configuration for (test) Java agents in Gradle projects.
 *
 * Provides utilities for managing Java agent dependencies during test execution and automatically
 * configuring necessary JVM arguments.
 *
 * Example usage in build.gradle.kts:
 * ```
 * dependencies {
 *   testJavaAgents {
 *     mockitoAgent() // Deprecated, please don't use Mockito
 *     javaAgent("com.example:custom-java-agent:1.0.0")
 *   }
 * }
 * ```
 */
public abstract class JavaAgentDependenciesExtension
@Inject
constructor(private val project: Project, private val foundryProperties: FoundryProperties) {

  @Deprecated("Don't use mockito for new tests.")
  public fun DependencyHandler.mockitoAgent() {
    foundryProperties.versions.mockito.ifPresent { javaAgent("org.mockito:mockito-core:$it") }
  }

  public fun DependencyHandler.javaAgent(dependencyNotation: Any) {
    val dependency = project.dependencies.create(dependencyNotation)
    add("testImplementation", dependency)
    val config =
      project.configurations.detachedConfiguration(dependency).apply { isTransitive = false }
    project.tasks.withType(Test::class.java).configureEach {
      jvmArgumentProviders.add(
        project.objects.newInstance<JavaAgentArgumentProvider>().apply {
          classpath.from(config.incoming.artifactView {}.files)
        }
      )
    }
  }

  internal companion object {
    fun register(project: Project, foundryProperties: FoundryProperties) =
      project.dependencies.extensions.create<JavaAgentDependenciesExtension>(
        /* name = */ "testJavaAgents",
        /* type = */ JavaAgentDependenciesExtension::class.java,
        /* ...constructionArguments = */ project,
        foundryProperties,
      )
  }
}
