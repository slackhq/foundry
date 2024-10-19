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
package foundry.gradle.fakes

import groovy.lang.Closure
import java.io.File
import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.StartParameter
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.ProjectEvaluationListener
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.initialization.IncludedBuild
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.invocation.GradleLifecycle
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.ObjectConfigurationAction
import org.gradle.api.plugins.PluginContainer
import org.gradle.api.plugins.PluginManager
import org.gradle.api.services.BuildServiceRegistry

/** A stub for the [Gradle] interface. It will return the [StartParameter] it is provided. */
class GradleStub(private val startParameter: StartParameter) : Gradle {

  override fun buildFinished(closure: Closure<*>) = Unit

  override fun buildFinished(action: Action<in BuildResult>) = Unit

  override fun addProjectEvaluationListener(
    listener: ProjectEvaluationListener
  ): ProjectEvaluationListener = TODO()

  override fun addListener(listener: Any) = Unit

  override fun getGradleHomeDir(): File? = null

  override fun addBuildListener(buildListener: BuildListener) = Unit

  override fun removeListener(listener: Any) = Unit

  @Suppress("UnstableApiUsage") override fun getSharedServices(): BuildServiceRegistry = TODO()

  override fun getGradleVersion(): String = ""

  override fun getPlugins(): PluginContainer = TODO()

  override fun afterProject(closure: Closure<*>) = Unit

  override fun afterProject(action: Action<in Project>) = Unit

  override fun getRootProject(): Project = TODO()

  override fun getGradle(): Gradle = this

  override fun getParent(): Gradle? = null

  override fun includedBuild(name: String): IncludedBuild = TODO()

  override fun getPluginManager(): PluginManager = TODO()

  override fun getExtensions(): ExtensionContainer {
    TODO()
  }

  override fun removeProjectEvaluationListener(listener: ProjectEvaluationListener) = Unit

  override fun getLifecycle(): GradleLifecycle {
    TODO("Not yet implemented")
  }

  override fun beforeSettings(closure: Closure<*>) = Unit

  override fun beforeSettings(action: Action<in Settings>) = Unit

  override fun useLogger(logger: Any) = Unit

  override fun getGradleUserHomeDir(): File = startParameter.gradleUserHomeDir

  override fun settingsEvaluated(closure: Closure<*>) = Unit

  override fun settingsEvaluated(action: Action<in Settings>) = Unit

  override fun allprojects(action: Action<in Project>) = Unit

  override fun projectsLoaded(closure: Closure<*>) = Unit

  override fun projectsLoaded(action: Action<in Gradle>) = Unit

  override fun apply(closure: Closure<*>) = Unit

  override fun apply(action: Action<in ObjectConfigurationAction>) = Unit

  override fun apply(options: MutableMap<String, *>) = Unit

  override fun beforeProject(closure: Closure<*>) = Unit

  override fun beforeProject(action: Action<in Project>) = Unit

  override fun getTaskGraph(): TaskExecutionGraph = TODO()

  override fun projectsEvaluated(closure: Closure<*>) = Unit

  override fun projectsEvaluated(action: Action<in Gradle>) = Unit

  override fun rootProject(action: Action<in Project>) = Unit

  override fun getIncludedBuilds(): MutableCollection<IncludedBuild> = mutableSetOf()

  override fun getStartParameter(): StartParameter = startParameter
}
