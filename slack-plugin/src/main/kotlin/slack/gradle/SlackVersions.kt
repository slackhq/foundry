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
import org.gradle.api.provider.Provider

// TODO generate something to map these in the future? Or with reflection?
internal class SlackVersions(val catalog: VersionCatalog) {
  val agp: String?
    get() = getOptionalValue("agp").orElse(null)
  val composeCompiler: String?
    get() = getOptionalValue("compose-compiler").orElse(null)
  val composeCompilerKotlinVersion: String?
    get() = getOptionalValue("compose-compiler-kotlin-version").orElse(null)
  val detekt: String?
    get() = getOptionalValue("detekt").orElse(null)
  val gjf: String?
    get() = getOptionalValue("google-java-format").orElse(null)
  val gson: String?
    get() = getOptionalValue("gson").orElse(null)
  val kotlin: String
    get() = getValue("kotlin")
  val ktlint: String?
    get() = getOptionalValue("ktlint").orElse(null)
  val ktfmt: String?
    get() = getOptionalValue("ktfmt").orElse(null)
  val objenesis: String?
    get() = getOptionalValue("objenesis").orElse(null)
  val jdk: Int
    get() = getValue("jdk").toInt()
  val jvmTarget: Int
    get() = getOptionalValue("jvmTarget").map { it.toInt() }.orElse(11)

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
  }

  internal fun getValue(key: String): String {
    return getOptionalValue(key).orElseThrow {
      IllegalStateException("No catalog version found for ${tomlKey(key)}")
    }
  }

  internal fun getOptionalValue(key: String): Optional<String> {
    val tomlKey = tomlKey(key)
    return catalog.findVersion(tomlKey).map { it.toString() }
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
