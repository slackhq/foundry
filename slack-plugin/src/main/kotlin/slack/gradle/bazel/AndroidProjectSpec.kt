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
package slack.gradle.bazel

import com.grab.grazel.bazel.starlark.BazelDependency
import com.grab.grazel.bazel.starlark.asString
import com.grab.grazel.bazel.starlark.glob
import com.grab.grazel.bazel.starlark.quote
import com.grab.grazel.bazel.starlark.statements
import okio.Path.Companion.toOkioPath
import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvableConfiguration
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.UntrackedTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import slack.gradle.SlackExtension
import slack.gradle.SlackProperties
import slack.gradle.bazel.Dep.Local.Companion.toBazelPath
import slack.gradle.register

/** A spec for a plain Kotlin Android project. */
internal class AndroidProjectSpec(builder: Builder) :
  CommonJvmProjectSpec by CommonJvmProjectSpec(builder) {

  val namespace: String = builder.namespace
  val manifest: String? = builder.manifest
  val resourceFiles: List<String> = builder.resourceFiles
  val resourceFilesGlobs: List<String> = builder.resourceFilesGlobs

  override fun toString(): String {
    // Write statements in roughly the order of operations for readability
    return statements {
        val (implDeps, testDeps, plugins) = writeCommonJvmStatements()
        val fullTestDeps = buildList {
          // Ensure we depend on the lib target
          // We have to do this because there's no kt_android_local_test that supports associated
          // compilations
          add(BazelDependency.StringDependency(Dep.Target(name).toString()))
          addAll(testDeps)
        }

        slackKtLibrary(
          name = name,
          ruleSource = ruleSource,
          packageName = namespace,
          kotlinProjectType = KotlinProjectType.Android,
          manifest = manifest,
          srcsGlob = srcGlobs,
          visibility = Visibility.Public,
          deps = implDeps,
          plugins = plugins,
          exportedDeps =
            exportedDeps.sorted().map { BazelDependency.StringDependency(it.toString()) },
          resources = resourceFiles,
          resourceFiles = listOf(glob(resourceFilesGlobs.map(String::quote))),
          kotlincOptions = freeCompilerArgs,
          // TODO
          //  assets
          //  viewbinding
        )

        if (hasTests) {
          slackKtAndroidLocalTest(
            name = CommonJvmProjectSpec.testName(name),
            ruleSource = ruleSource,
            associates = listOf(BazelDependency.StringDependency(":$name")),
            srcsGlob = testSrcGlobs,
            plugins = plugins,
            deps = fullTestDeps,
            kotlincOptions = testFreeCompilerArgs,
          )
        }
        // TODO android tests
        //  jvm w/ stub jar
        //  androidTest
      }
      .asString()
  }

  class Builder(override val name: String, override val path: String, val namespace: String) :
    CommonJvmProjectSpec.Builder<Builder> {
    var manifest: String? = null
    // TODO unclear if bazel supports test-only resources
    val resourceFiles = mutableListOf<String>()
    val resourceFilesGlobs = mutableListOf("src/main/res/**")

    // Inherited from CommonJvmProjectSpec
    override var ruleSource = "@rules_kotlin//kotlin:jvm.bzl"
    override val deps = mutableListOf<Dep>()
    override val exportedDeps = mutableListOf<Dep>()
    override var hasTests: Boolean = false
    override val testDeps = mutableListOf<Dep>()
    override val srcGlobs = mutableListOf("src/main/**/*.kt", "src/main/**/*.java")
    override val testSrcGlobs = mutableListOf("src/test/**/*.kt", "src/test/**/*.java")
    override val compilerPlugins = mutableListOf<Dep>()
    override val kspProcessors = mutableListOf<KspProcessor>()
    override val freeCompilerArgs = mutableListOf<String>()
    override val testFreeCompilerArgs = mutableListOf<String>()

    fun manifest(manifest: String) = apply { this.manifest = manifest }

    fun addResourceFile(file: String) = apply { this.resourceFiles += file }

    fun addResourceFilesGlob(glob: String) = apply { this.resourceFilesGlobs += glob }

    fun build(): AndroidProjectSpec = AndroidProjectSpec(this)
  }
}

@UntrackedTask(because = "Generates a Bazel BUILD file for a Kotlin JVM project")
internal abstract class AndroidProjectBazelTask : DefaultTask(), CommonJvmProjectBazelTask {

  @get:Input abstract val namespace: Property<String>
  @get:Input @get:Optional abstract val manifest: Property<String>
  @get:Input @get:Optional abstract val enableRobolectric: Property<Boolean>
  @get:Input @get:Optional abstract val enableCompose: Property<Boolean>
  @get:Input abstract val robolectricCoreDeps: SetProperty<Dep>

  init {
    group = "bazel"
    description = "Generates a Bazel BUILD file for a Kotlin JVM project"
  }

  @TaskAction
  fun generate() {
    AndroidProjectSpec.Builder(targetName.get(), projectPath.get().toBazelPath(), namespace.get())
      .applyCommonJvmConfig()
      .apply {
        this@AndroidProjectBazelTask.manifest.orNull?.let(::manifest)
        val robolectricEnabled = this@AndroidProjectBazelTask.enableRobolectric.getOrElse(false)
        if (robolectricEnabled) {
          for (dep in robolectricCoreDeps.get()) {
            addTestDep(dep)
          }
        }
        if (this@AndroidProjectBazelTask.enableCompose.getOrElse(false)) {
          addCompilerPlugin(CompilerPluginDeps.compose)
        }
      }
      .build()
      .writeTo(outputFile.asFile.get().toOkioPath())
  }

  companion object {
    fun register(
      project: Project,
      slackProperties: SlackProperties,
      depsConfiguration: NamedDomainObjectProvider<ResolvableConfiguration>,
      exportedDepsConfiguration: NamedDomainObjectProvider<ResolvableConfiguration>,
      testConfiguration: NamedDomainObjectProvider<ResolvableConfiguration>?,
      kspConfiguration: NamedDomainObjectProvider<ResolvableConfiguration>?,
      kaptConfiguration: NamedDomainObjectProvider<ResolvableConfiguration>?,
      slackExtension: SlackExtension,
      namespace: Provider<String>,
      kotlinCompilation: TaskProvider<KotlinCompile>,
      testKotlinCompilation: TaskProvider<KotlinCompile>,
    ) {
      // TODO multiple variants?
      project.tasks.register<AndroidProjectBazelTask>("generateBazel") {
        this.namespace.set(namespace)
        if (project.file("src/main/AndroidManifest.xml").exists()) {
          manifest.set("src/main/AndroidManifest.xml")
        }
        // TODO plumb compiler options
        enableCompose.set(slackExtension.featuresHandler.composeHandler.enabled)
        // TODO what about android.testOptions.unitTests.isIncludeAndroidResources?
        enableRobolectric.set(slackExtension.androidHandler.featuresHandler.robolectric)
        robolectricCoreDeps.addAll(
          Dep.Remote.fromMavenIdentifier("org.robolectric:annotations"),
          Dep.Remote.fromMavenIdentifier("org.robolectric:robolectric"),
          Dep.Remote(source = "robolectric", path = "bazel", target = "android-all"),
          Dep.Local.fromGradlePath(slackProperties.robolectricCoreProjectPath),
        )
        configureCommonJvm(
          project,
          slackProperties,
          depsConfiguration,
          exportedDepsConfiguration,
          testConfiguration,
          kspConfiguration,
          kaptConfiguration,
          slackExtension,
          kotlinCompilation,
          testKotlinCompilation,
        )
      }
    }
  }
}
