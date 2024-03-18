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

import com.grab.grazel.bazel.rules.rule
import com.grab.grazel.bazel.starlark.Assignee
import com.grab.grazel.bazel.starlark.BazelDependency
import com.grab.grazel.bazel.starlark.StatementsBuilder
import com.grab.grazel.bazel.starlark.array
import com.grab.grazel.bazel.starlark.asString
import com.grab.grazel.bazel.starlark.glob
import com.grab.grazel.bazel.starlark.load
import com.grab.grazel.bazel.starlark.quote

internal enum class KotlinProjectType {
  Android,
  Jvm
}

internal enum class Visibility(val rule: String) {
  Public("//visibility:public"),
  Private("//visibility:private"),
}

internal fun StatementsBuilder.loadKtRules(kotlinProjectType: KotlinProjectType) {
  when (kotlinProjectType) {
    KotlinProjectType.Android -> load("//bazel/macros:module.bzl", "kt_android_library")
    KotlinProjectType.Jvm -> load("//bazel/macros:module.bzl", "kt_jvm_library", "kt_jvm_test")
  }
}

// Fork of the built-in ktLibrary that supports exportedDeps
internal fun StatementsBuilder.slackKtLibrary(
  name: String,
  kotlinProjectType: KotlinProjectType = KotlinProjectType.Jvm,
  srcs: List<String> = emptyList(),
  packageName: String? = null,
  srcsGlob: List<String> = emptyList(),
  visibility: Visibility = Visibility.Public,
  deps: List<BazelDependency> = emptyList(),
  exportedDeps: List<BazelDependency> = emptyList(),
  resources: List<String> = emptyList(),
  resourceFiles: List<Assignee> = emptyList(),
  manifest: String? = null,
  plugins: List<BazelDependency> = emptyList(),
  assetsGlob: List<String> = emptyList(),
  assetsDir: String? = null,
  tags: List<String> = emptyList(),
) {
  loadKtRules(kotlinProjectType)
  val ruleName =
    when (kotlinProjectType) {
      KotlinProjectType.Jvm -> "kt_jvm_library"
      KotlinProjectType.Android -> "kt_android_library"
    }

  rule(ruleName) {
    "name" `=` name.quote
    srcs.notEmpty { "srcs" `=` srcs.map(String::quote) }
    srcsGlob.notEmpty { "srcs" `=` glob(srcsGlob.map(String::quote)) }
    "visibility" `=` array(visibility.rule.quote)
    deps.notEmpty { "deps" `=` array(deps.map(BazelDependency::toString).map(String::quote)) }
    exportedDeps.notEmpty {
      "exports" `=` array(exportedDeps.map(BazelDependency::toString).map(String::quote))
    }
    resourceFiles.notEmpty {
      "resource_files" `=`
        resourceFiles.joinToString(separator = " + ", transform = Assignee::asString)
    }
    resources.notEmpty { "resource_files" `=` glob(resources.quote) }
    packageName?.let { "custom_package" `=` packageName.quote }
    manifest?.let { "manifest" `=` manifest.quote }
    plugins.notEmpty {
      "plugins" `=` array(plugins.map(BazelDependency::toString).map(String::quote))
    }
    assetsDir?.let {
      "assets" `=` glob(assetsGlob.quote)
      "assets_dir" `=` assetsDir.quote
    }

    tags.notEmpty { "tags" `=` array(tags.map(String::quote)) }
  }
}

internal fun StatementsBuilder.slackKtTest(
  name: String,
  kotlinProjectType: KotlinProjectType = KotlinProjectType.Jvm,
  srcs: List<String> = emptyList(),
  srcsGlob: List<String> = emptyList(),
  associates: List<BazelDependency> = emptyList(),
  deps: List<BazelDependency> = emptyList(),
  plugins: List<BazelDependency> = emptyList(),
  tags: List<String> = emptyList(),
) {
  loadKtRules(kotlinProjectType)
  rule("kt_jvm_test") {
    "name" `=` name.quote
    srcs.notEmpty { "srcs" `=` srcs.map(String::quote) }
    srcsGlob.notEmpty { "srcs" `=` glob(srcsGlob.map(String::quote)) }
    deps.notEmpty { "deps" `=` array(deps.map(BazelDependency::toString).map(String::quote)) }
    associates.notEmpty {
      "associates" `=` array(associates.map(BazelDependency::toString).map(String::quote))
    }
    plugins.notEmpty {
      "plugins" `=` array(plugins.map(BazelDependency::toString).map(String::quote))
    }
    tags.notEmpty { "tags" `=` array(tags.map(String::quote)) }
  }
}

internal object CompilerPluginDeps {
  val moshix = BazelDependency.StringDependency("//third_party:moshix")
  val redacted = BazelDependency.StringDependency("//third_party:redacted")
  val parcelize = BazelDependency.StringDependency("//third_party:parcelize")
}
