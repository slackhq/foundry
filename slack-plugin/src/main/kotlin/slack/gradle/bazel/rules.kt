package slack.gradle.bazel

import com.grab.grazel.bazel.rules.KotlinProjectType
import com.grab.grazel.bazel.rules.rule
import com.grab.grazel.bazel.starlark.Assignee
import com.grab.grazel.bazel.starlark.BazelDependency
import com.grab.grazel.bazel.starlark.StatementsBuilder
import com.grab.grazel.bazel.starlark.array
import com.grab.grazel.bazel.starlark.asString
import com.grab.grazel.bazel.starlark.glob
import com.grab.grazel.bazel.starlark.load
import com.grab.grazel.bazel.starlark.quote

internal enum class Visibility(val rule: String) {
  Public("//visibility:public"),
  Private("//visibility:private"),
}

internal fun StatementsBuilder.loadKtRules(kotlinProjectType: KotlinProjectType) {
  when (kotlinProjectType) {
    is KotlinProjectType.Android -> load("//bazel/macros:module.bzl", "kt_android_library")
    KotlinProjectType.Jvm -> load("//bazel/macros:module.bzl", "kt_jvm_library")
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
      is KotlinProjectType.Android ->
        if (kotlinProjectType.hasDatabinding) {
          "kt_db_android_library"
        } else {
          "kt_android_library"
        }
    }

  rule(ruleName) {
    "name" eq name.quote()
    srcs.notEmpty { "srcs" eq srcs.map(String::quote) }
    srcsGlob.notEmpty { "srcs" eq glob(srcsGlob.map(String::quote)) }
    "visibility" eq array(visibility.rule.quote())
    deps.notEmpty { "deps" eq array(deps.map(BazelDependency::toString).map(String::quote)) }
    exportedDeps.notEmpty {
      "exportedDeps" eq array(exportedDeps.map(BazelDependency::toString).map(String::quote))
    }
    resourceFiles.notEmpty {
      "resource_files" eq
        resourceFiles.joinToString(separator = " + ", transform = Assignee::asString)
    }
    resources.notEmpty { "resource_files" eq glob(resources.quote) }
    packageName?.let { "custom_package" eq packageName.quote() }
    manifest?.let { "manifest" eq manifest.quote() }
    plugins.notEmpty {
      "plugins" eq array(plugins.map(BazelDependency::toString).map(String::quote))
    }
    assetsDir?.let {
      "assets" eq glob(assetsGlob.quote)
      "assets_dir" eq assetsDir.quote()
    }

    tags.notEmpty { "tags" eq array(tags.map(String::quote)) }
  }
}

internal fun StatementsBuilder.slackKtTest(
  name: String,
  kotlinProjectType: KotlinProjectType = KotlinProjectType.Jvm,
  srcs: List<String> = emptyList(),
  srcsGlob: List<String> = emptyList(),
  visibility: Visibility = Visibility.Public,
  associates: List<BazelDependency> = emptyList(),
  deps: List<BazelDependency> = emptyList(),
  plugins: List<BazelDependency> = emptyList(),
  tags: List<String> = emptyList(),
) {
  loadKtRules(kotlinProjectType)
  rule("kt_jvm_test") {
    "name" eq name.quote()
    srcs.notEmpty { "srcs" eq srcs.map(String::quote) }
    srcsGlob.notEmpty { "srcs" eq glob(srcsGlob.map(String::quote)) }
    "visibility" eq array(visibility.rule.quote())
    deps.notEmpty { "deps" eq array(deps.map(BazelDependency::toString).map(String::quote)) }
    associates.notEmpty {
      "associates" eq array(associates.map(BazelDependency::toString).map(String::quote))
    }
    plugins.notEmpty {
      "plugins" eq array(plugins.map(BazelDependency::toString).map(String::quote))
    }
    tags.notEmpty { "tags" eq array(tags.map(String::quote)) }
  }
}

internal object CompilerPluginDeps {
  val moshix = BazelDependency.StringDependency("//third_party:moshix")
  val redacted = BazelDependency.StringDependency("//third_party:redacted")
  val parcelize = BazelDependency.StringDependency("//third_party:parcelize")
}
