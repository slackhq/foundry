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
import com.grab.grazel.bazel.starlark.statements
import okio.Path.Companion.toOkioPath
import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvableConfiguration
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import slack.gradle.SlackExtension
import slack.gradle.SlackProperties
import slack.gradle.bazel.Dep.Local.Companion.toBazelPath
import slack.gradle.register

/** A spec for a plain kotlin jvm project. */
internal class JvmProjectSpec(builder: Builder) :
  CommonJvmProjectSpec by CommonJvmProjectSpec(builder) {
  override fun toString(): String {
    /*
     load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library", "kt_jvm_test")

     kt_jvm_library(
         name = "ditto_lib",
         srcs = glob(["src/main/**/*.kt"]),
         visibility = ["//visibility:public"],
         deps = [
             "@maven//:androidx_annotation_annotation",
         ],
     )

     kt_jvm_test(
         name = "ditto_test",
         srcs = glob(["src/test/**/*.kt"]),
         visibility = ["//visibility:private"],
         deps = [
             ":ditto_lib",
             "@maven//:androidx_annotation_annotation",
             "@maven//:org_jetbrains_kotlinx_kotlinx_coroutines_test",
             "@maven//:org_jetbrains_kotlin_kotlin_test",
             "@maven//:junit_junit",
             "@maven//:com_google_truth_truth",
         ],
     )
    */

    // Write statements in roughly the order of operations for readability
    return statements {
        val (implDeps, testDeps, plugins) = writeCommonJvmStatements()

        slackKtLibrary(
          name = name,
          ruleSource = ruleSource,
          kotlinProjectType = KotlinProjectType.Jvm,
          srcsGlob = srcGlobs,
          visibility = Visibility.Public,
          deps = implDeps,
          plugins = plugins,
          exportedDeps =
            exportedDeps.sorted().map { BazelDependency.StringDependency(it.toString()) },
        )

        if (hasTests) {
          // TODO only generate if there are actually matching test sources?
          slackKtTest(
            name = CommonJvmProjectSpec.testName(name),
            ruleSource = ruleSource,
            associates = listOf(BazelDependency.StringDependency(":$name")),
            kotlinProjectType = KotlinProjectType.Jvm,
            srcsGlob = testSrcGlobs,
            plugins = plugins,
            deps = testDeps,
          )
        }
      }
      .asString()
  }

  class Builder(override val name: String, override val path: String) :
    CommonJvmProjectSpec.Builder<Builder> {
    override var ruleSource = "@rules_kotlin//kotlin:jvm.bzl"
    override val deps = mutableListOf<Dep>()
    override val exportedDeps = mutableListOf<Dep>()
    override var hasTests: Boolean = false
    override val testDeps = mutableListOf<Dep>()
    override val srcGlobs = mutableListOf("src/main/**/*.kt", "src/main/**/*.java")
    override val testSrcGlobs = mutableListOf("src/test/**/*.kt", "src/test/**/*.java")
    override val compilerPlugins = mutableListOf<Dep>()
    override val kspProcessors = mutableListOf<KspProcessor>()

    fun build(): JvmProjectSpec = JvmProjectSpec(this)
  }
}

@UntrackedTask(because = "Generates a Bazel BUILD file for a Kotlin JVM project")
internal abstract class JvmProjectBazelTask : DefaultTask(), CommonJvmProjectBazelTask {

  init {
    group = "bazel"
    description = "Generates a Bazel BUILD file for a Kotlin JVM project"
  }

  @TaskAction
  fun generate() {
    JvmProjectSpec.Builder(targetName.get(), projectPath.get().toBazelPath())
      .applyCommonJvmConfig()
      .build()
      .writeTo(outputFile.asFile.get().toOkioPath())
  }

  companion object {
    fun register(
      project: Project,
      slackProperties: SlackProperties,
      depsConfiguration: NamedDomainObjectProvider<ResolvableConfiguration>,
      exportedDepsConfiguration: NamedDomainObjectProvider<ResolvableConfiguration>,
      testConfiguration: NamedDomainObjectProvider<ResolvableConfiguration>,
      kspConfiguration: NamedDomainObjectProvider<ResolvableConfiguration>?,
      kaptConfiguration: NamedDomainObjectProvider<ResolvableConfiguration>?,
      slackExtension: SlackExtension,
    ) {
      project.tasks.register<JvmProjectBazelTask>("generateBazel") {
        configureCommonJvm(
          project,
          slackProperties,
          depsConfiguration,
          exportedDepsConfiguration,
          testConfiguration,
          kspConfiguration,
          kaptConfiguration,
          slackExtension,
        )
      }
    }
  }
}
