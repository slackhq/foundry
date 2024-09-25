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
package slack.gradle

import java.util.Optional
import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.provider.Provider

/**
 * A set of properties corresponding to *version* aliases in a [catalog]. The keys should be written
 * as they appear in the toml file.
 */
// TODO generate something to map these in the future? Or with reflection?
internal class SlackVersions(val catalog: VersionCatalog) {
  val agp: String?
    get() = getOptionalValue("agp").orElse(null)

  val detekt: String?
    get() = getOptionalValue("detekt").orElse(null)

  val gjf: String?
    get() = getOptionalValue("googleJavaFormat").orElse(null)

  val gson: String?
    get() = getOptionalValue("gson").orElse(null)

  val kotlin: String
    get() = getValue("kotlin")

  val ktlint: String?
    get() = getOptionalValue("ktlint").orElse(null)

  val ktfmt: String?
    get() = getOptionalValue("ktfmt").orElse(null)

  val sortDependencies: String?
    get() = getOptionalValue("sortDependencies").orElse(null)

  val objenesis: String?
    get() = getOptionalValue("objenesis").orElse(null)

  /** The JDK version to use for compilations. */
  val jdk: Optional<Int>
    get() =
      getOptionalValue("jdk")
        .flatMap { Optional.of(it.toInt()) }
        .also { check(it.isPresent) { "A `jdk` version must be defined in libs.versions.toml" } }

  /** The JDK runtime to target for compilations. */
  // Can't use OptionalInt because it lacks mapping functions
  val jvmTarget: Optional<Int>
    get() = getOptionalValue("jvmTarget").flatMap { Optional.of(it.toInt()) }.or { Optional.of(17) }

  val composeJb: String?
    get() = getOptionalValue("compose-jb").orElse(null)

  val composeJbKotlinVersion: String?
    get() = getOptionalValue("compose-jb-kotlinVersion").orElse(null)

  val robolectric: String?
    get() = getOptionalValue("robolectric").orElse(null)

  fun lookupVersion(key: String) = getOptionalValue(key)

  val bundles = Bundles()

  inner class Bundles {
    val commonAnnotations: Optional<Provider<ExternalModuleDependencyBundle>> by lazy {
      catalog.findBundle("common-annotations")
    }
    val commonLint: Optional<Provider<ExternalModuleDependencyBundle>> by lazy {
      catalog.findBundle("common-lint")
    }
    val commonTest: Optional<Provider<ExternalModuleDependencyBundle>> by lazy {
      catalog.findBundle("common-test")
    }
    val commonCircuit: Optional<Provider<ExternalModuleDependencyBundle>> by lazy {
      catalog.findBundle("common-circuit")
    }
  }

  internal fun getValue(key: String): String {
    return getOptionalValue(key).orElseThrow {
      IllegalStateException("No catalog version found for ${tomlKey(key)}")
    }
  }

  private fun getOptionalValue(key: String): Optional<String> {
    return catalog.findVersion(key).map(VersionConstraint::toString)
  }

  internal val boms: Set<Provider<MinimalExternalModuleDependency>> by lazy {
    catalog.libraryAliases
      .filter {
        // Library alias is as it appears in usage, not as it appears in the toml
        // So, "coroutines-bom" in the toml is "coroutines.bom" in usage
        it.endsWith(".bom")
      }
      .mapTo(LinkedHashSet()) { catalog.findLibrary(it).get() }
  }
}
