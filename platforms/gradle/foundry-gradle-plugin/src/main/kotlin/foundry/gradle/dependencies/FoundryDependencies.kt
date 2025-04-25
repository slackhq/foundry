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
package foundry.gradle.dependencies

import foundry.gradle.dependencies.FoundryDependencies.artifact

/**
 * Foundry dependencies! Please keep these in order:
 * - Properties before types
 * - Alphabetical order
 *
 * **NOTE:** This is _not_ where versions are defined! Those are defined in `gradle.properties`.
 * These definitions are solely just pointers to coordinate identifiers and the versions are
 * resolved via gradle properties at configuration time. Version keys are defined in the format of
 * either
 * * DependencySet member - `slack.dependencies.artifact-id-here=version-here`
 * * DependencyGroup member - `slack.dependencies.dependency-group-simple-name-here=version-here`
 *
 * These gradle properties can be customized at both the [DependencyGroup] constructor and
 * [artifact] function call levels. All properties are eventually prefixed with
 * `slack.dependencies.` under the hood, so there is no need to manually specify it here.
 *
 * The main entry point to define an artifact is via [artifact]. This can be depended on in
 * build.gradle (exposed as a [Notation] shorthand, which gradle dependencies can understand). Under
 * the hood this is backed by a delegate that contains a full [DependencyDef] that we can use for
 * further dependency analysis elsewhere.
 *
 * ```
 * val dependencyName: Any by artifact(...)
 * ```
 *
 * If added context is needed, you can add `withComments` as an infix function. These correspond to
 * Gradle's `because` semantic in dependencies.
 *
 * ```
 * val dependencyName: Any by artifact(...) withComments "This is why this is here!"
 * ```
 *
 * When declaring a set of unrelated dependencies, use [DependencySet].
 *
 * ```
 * object Name : DependencySet() {
 *   public val dependencyName: Any by artifact(group, artifact)
 *
 *   // If the property name can be used as the artifact name, you can just define the group
 *   public val gson: Any by artifact(group) // artifact name is implicitly 'gson'
 * }
 * ```
 *
 * When declaring a group of related dependencies, use [DependencyGroup].
 *
 * ```
 * object Name : DependencyGroup(group) {
 *   public val dependencyName: Any by artifact(artifact)
 *
 *   // Artifact can be implied here too
 *   public val gson: Any by artifact() // artifact name is implicitly 'gson'
 *
 *   // You can override specific values
 *   public val slightlyOdd: Any by artifact(groupOverride = "differentGroup")
 * }
 * ```
 */
@Suppress("MemberNameEqualsClassName") // Detekt is being silly here
internal object FoundryDependencies : DependencySet() {

  internal val javaxInject: Any by artifact("javax.inject", "javax.inject")
  internal val jakarta: Any by artifact("jakarta.inject", "inject")

  object Auto : DependencySet() {
    val common: Any by artifact("com.google.auto", "auto-common")

    object Service : DependencyGroup("com.google.auto.service", "auto-service") {
      // TODO Switch to using slack.features.autovalue in the build file.
      val autoservice: Any by artifact("auto-service")
      // TODO Switch to using slack.features.autovalue in the build file.
      val ksp: Any by
        artifact(
          groupOverride = "dev.zacsweers.autoservice",
          artifact = "auto-service-ksp",
          gradleProperty = "auto-service-ksp",
        )
      // Intentionally public as we use AutoService annotations only in some places
      val annotations: Any by artifact("auto-service-annotations")
    }
  }

  internal object Dagger : DependencyGroup("com.google.dagger") {
    val compiler: Any by artifact("dagger-compiler")
    val dagger: Any by artifact()
  }

  object ErrorProne : DependencyGroup("com.google.errorprone") {
    val annotations: Any by artifact("error_prone_annotations")
    internal val core: Any by artifact("error_prone_core")
  }

  object Moshi : DependencyGroup("com.squareup.moshi") {
    val adapters: Any by artifact("moshi-adapters")
    val codeGen: Any by artifact("moshi-kotlin-codegen")
    val moshi: Any by artifact()
    val kotlinReflect: Any by artifact("moshi-kotlin")
    val lazyAdapters: Any by
      artifact(groupOverride = "com.serjltt.moshi", artifact = "moshi-lazy-adapters")

    object MoshiX : DependencyGroup("dev.zacsweers.moshix") {
      val adapters: Any by artifact("moshi-adapters")
      val metadataReflect: Any by artifact("moshi-metadata-reflect")

      object Sealed : DependencyGroup(parent = MoshiX) {
        val runtime: Any by artifact("moshi-sealed-runtime")
        val reflect: Any by artifact("moshi-sealed-reflect")
        val metadataReflect: Any by artifact("moshi-sealed-metadata-reflect")
        val codegen: Any by artifact("moshi-sealed-codegen")
      }
    }
  }

  object Testing : DependencySet() {
    internal object Robolectric : DependencyGroup("org.robolectric") {
      val annotations: Any by artifact()
      val robolectric: Any by artifact()
      val pluginapi: Any by artifact()
    }
  }
}
