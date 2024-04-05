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
import java.io.Serializable

internal enum class KotlinProjectType {
  Android,
  Jvm
}

internal enum class Visibility(val rule: String) {
  Public("//visibility:public"),
  Private("//visibility:private"),
}

internal fun StatementsBuilder.loadKtRules(
  kotlinProjectType: KotlinProjectType,
  ruleSource: String,
) {
  when (kotlinProjectType) {
    KotlinProjectType.Android -> load(ruleSource, "kt_android_library")
    KotlinProjectType.Jvm -> load(ruleSource, "kt_jvm_library", "kt_jvm_test")
  }
}

// Fork of the built-in ktLibrary that supports exportedDeps
internal fun StatementsBuilder.slackKtLibrary(
  name: String,
  ruleSource: String,
  kotlinProjectType: KotlinProjectType = KotlinProjectType.Jvm,
  srcs: List<String> = emptyList(),
  packageName: String? = null,
  srcsGlob: List<String> = emptyList(),
  visibility: Visibility = Visibility.Public,
  deps: List<BazelDependency> = emptyList(),
  exportedDeps: List<BazelDependency> = emptyList(),
  resources: List<String> = emptyList(),
  resourceFiles: List<Assignee> = emptyList(),
  kotlincOptions: List<String> = emptyList(),
  manifest: String? = null,
  plugins: List<BazelDependency> = emptyList(),
  assetsGlob: List<String> = emptyList(),
  assetsDir: String? = null,
  tags: List<String> = emptyList(),
) {
  loadKtRules(kotlinProjectType, ruleSource)
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
    kotlincOptions.notEmpty { "kt_kotlinc_options" `=` array(kotlincOptions.map(String::quote)) }

    tags.notEmpty { "tags" `=` array(tags.map(String::quote)) }
  }
}

internal fun StatementsBuilder.slackKtTest(
  name: String,
  ruleSource: String,
  kotlinProjectType: KotlinProjectType = KotlinProjectType.Jvm,
  srcs: List<String> = emptyList(),
  srcsGlob: List<String> = emptyList(),
  associates: List<BazelDependency> = emptyList(),
  deps: List<BazelDependency> = emptyList(),
  plugins: List<BazelDependency> = emptyList(),
  kotlincOptions: List<String> = emptyList(),
  tags: List<String> = emptyList(),
) {
  loadKtRules(kotlinProjectType, ruleSource)
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
    kotlincOptions.notEmpty { "kt_kotlinc_options" `=` array(kotlincOptions.map(String::quote)) }
  }
}

@Suppress("UNREACHABLE_CODE", "UNUSED_PARAMETER")
internal fun StatementsBuilder.slackKtAndroidLocalTest(
  name: String,
  srcs: List<String> = emptyList(),
  srcsGlob: List<String> = emptyList(),
  associates: List<BazelDependency> = emptyList(),
  deps: List<BazelDependency> = emptyList(),
  plugins: List<BazelDependency> = emptyList(),
  kotlincOptions: List<String> = emptyList(),
  customPackage: String? = null,
  testClass: String? = null,
  tags: List<String> = emptyList(),
) {
  // TODO no kt_android_local_test https://github.com/bazelbuild/rules_kotlin/issues/375
  return
  load("@rules_android//android:rules.bzl", "android_local_test")
  rule("android_local_test") {
    "name" `=` name.quote
    srcs.notEmpty { "srcs" `=` srcs.map(String::quote) }
    srcsGlob.notEmpty { "srcs" `=` glob(srcsGlob.map(String::quote)) }
    deps.notEmpty { "deps" `=` array(deps.map(BazelDependency::toString).map(String::quote)) }
    // TODO https://github.com/bazelbuild/rules_kotlin/issues/375
    associates.notEmpty {
      "associates" `=` array(associates.map(BazelDependency::toString).map(String::quote))
    }
    plugins.notEmpty {
      "plugins" `=` array(plugins.map(BazelDependency::toString).map(String::quote))
    }
    tags.notEmpty { "tags" `=` array(tags.map(String::quote)) }
    customPackage?.let { "custom_package" `=` it.quote }
    testClass?.let { "test_class" `=` it.quote }
    kotlincOptions.notEmpty { "kt_kotlinc_options" `=` array(kotlincOptions.map(String::quote)) }
  }
}

internal data class KspProcessor(
  val name: String,
  val processorProviderClass: String,
  val deps: Set<String>,
) : Serializable {
  fun withAddedDeps(deps: List<String>) = copy(deps = this.deps + deps)
}

internal fun StatementsBuilder.writeKspRule(processor: KspProcessor) {
  return kspProcessor(
    processor.name,
    processor.processorProviderClass,
    processor.deps.map(BazelDependency::StringDependency),
  )
}

internal fun StatementsBuilder.kspProcessor(
  name: String,
  processorProviderClass: String,
  deps: List<BazelDependency>,
) {
  load("@rules_kotlin//kotlin:core.bzl", "kt_ksp_plugin")
  rule("kt_ksp_plugin") {
    "name" `=` name.quote
    "processor_class" `=` processorProviderClass.quote
    "visibility" `=` array(Visibility.Private.rule.quote)
    "deps" `=` array(deps.map(BazelDependency::toString).map(String::quote))
  }
}

internal object CompilerPluginDeps {
  val compose = Dep.Local("third_party", target = "jetpack_compose_compiler_plugin")
  val moshix = Dep.Local("third_party", target = "moshix")
  val redacted = Dep.Local("third_party", target = "redacted")
  val parcelize = Dep.Local("third_party", target = "parcelize")
}

internal object KspProcessors {
  val moshiProguardRuleGen =
    KspProcessor(
      name = "moshix_proguard_rulegen",
      processorProviderClass =
        "dev.zacsweers.moshix.proguardgen.MoshiProguardGenSymbolProcessor\$Provider",
      deps =
        setOf(
          "@maven-slack//:com_squareup_kotlinpoet_jvm",
          "@maven-slack//:com_squareup_kotlinpoet_ksp",
          "@maven-slack//:com_squareup_moshi_moshi",
          "@maven-slack//:com_squareup_moshi_moshi_kotlin_codegen",
          "@maven-slack//:dev_zacsweers_moshix_moshi_proguard_rule_gen",
        ),
    )
  val autoService =
    KspProcessor(
      name = "autoservice",
      processorProviderClass = "dev.zacsweers.autoservice.ksp.AutoServiceSymbolProcessor\$Provider",
      deps =
        setOf(
          "@maven-slack//:com_google_auto_service_auto_service_annotations",
          "@maven-slack//:dev_zacsweers_autoservice_auto_service_ksp",
        ),
    )

  // TODO expose a way to add custom mappings
  val featureFlag =
    KspProcessor(
      name = "feature_flag_compiler",
      processorProviderClass =
        "slack.features.annotation.codegen.FeatureFlagSymbolProcessor\$Provider",
      deps =
        setOf(
          "@maven-slack//:com_squareup_anvil_annotations",
          "@maven-slack//:com_squareup_kotlinpoet_jvm",
          "@maven-slack//:com_squareup_kotlinpoet_ksp",
          "//libraries/foundation/feature-flag",
          "//libraries/foundation/slack-di",
        ),
    )

  val guinness =
    KspProcessor(
      name = "guinness_compiler",
      processorProviderClass = "slack.guinness.compiler.GuinnessSymbolProcessorProvider",
      deps =
        setOf(
          "@maven-slack//:com_squareup_anvil_annotations",
          "@maven-slack//:com_google_dagger",
          "@maven-slack//:com_squareup_retrofit",
          "@maven-slack//:com_squareup_kotlinpoet_jvm",
          "@maven-slack//:com_squareup_kotlinpoet_ksp",
          "@maven-slack//:slack_internal_vulcan_guinness",
          "@maven-slack//:slack_internal_vulcan_guinness_compiler",
        ),
    )
}
