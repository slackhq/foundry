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
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
  id("foundry.spotless")
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.multiplatform) apply false
  alias(libs.plugins.kotlin.plugin.sam) apply false
  alias(libs.plugins.detekt)
  alias(libs.plugins.mavenPublish) apply false
  alias(libs.plugins.dokka)
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.dependencyAnalysis)
  alias(libs.plugins.sortDependencies) apply false
  alias(libs.plugins.intellij) apply false
  alias(libs.plugins.pluginUploader) apply false
  alias(libs.plugins.buildConfig) apply false
  alias(libs.plugins.lint) apply false
  alias(libs.plugins.wire) apply false
  alias(libs.plugins.graphAssert) apply false
}

buildscript {
  dependencies {
    // Apply boms for buildscript classpath
    classpath(platform(libs.asm.bom))
    classpath(platform(libs.kotlin.bom))
    classpath(platform(libs.coroutines.bom))
    classpath(platform(libs.kotlin.gradlePlugins.bom))
  }
}

// Several plugins don't support isolated projects and need to be conditionally applied
val isolatedProjectsEnabled =
  providers
    .gradleProperty("org.gradle.unsafe.isolated-projects")
    .map { it.toBoolean() }
    .getOrElse(false)

// GraphAssert doesn't support isolated projects as it needs to traverse subproject configurations
// See: https://github.com/jraska/modules-graph-assert/issues/XXX (TODO: file upstream issue)
if (!isolatedProjectsEnabled) {
  apply(plugin = "com.jraska.module.graph.assertion")

  // Use dynamic configuration since the plugin classes aren't on compile classpath with 'apply
  // false'
  val ext = extensions.getByName("moduleGraphAssert")
  val extClass = ext.javaClass

  // Set allowed patterns - platforms can depend on tools but not the other way around
  extClass
    .getMethod("setAllowed", Array<String>::class.java)
    .invoke(
      ext,
      arrayOf(
        ":platforms.* -> :tools.*",
        ":platforms:gradle.* -> :platforms:gradle.*",
        ":platforms:intellij.* -> :platforms:intellij.*",
        ":tools.* -> :tools.*",
      ),
    )

  // Set configurations to analyze
  extClass
    .getMethod("setConfigurations", Set::class.java)
    .invoke(ext, setOf("api", "implementation"))
}

configure<DetektExtension> {
  toolVersion = libs.versions.detekt.get()
  allRules = true
}

tasks.withType<Detekt>().configureEach {
  jvmTarget = "21"
  reports {
    html.required.set(true)
    xml.required.set(true)
    txt.required.set(true)
  }
}

val spotlightEnabled =
  providers.gradleProperty("spotlight.enabled").map { it.toBoolean() }.getOrElse(true)

if (!spotlightEnabled) {
  dokka {
    dokkaPublications.html {
      outputDirectory.set(rootDir.resolve("docs/api/0.x"))
      includes.from(project.layout.projectDirectory.file("README.md"))
    }
  }

  dependencies {
    dokka(project(":tools:cli"))
    dokka(project(":tools:foundry-common"))
    dokka(project(":tools:skippy"))
    dokka(project(":tools:tracing"))
    dokka(project(":tools:version-number"))
    dokka(project(":platforms:gradle:better-gradle-properties"))
    dokka(project(":platforms:gradle:foundry-gradle-plugin"))
    dokka(project(":platforms:gradle:agp-handlers:agp-handler-api"))
  }
}

dependencyAnalysis {
  abi {
    exclusions {
      ignoreInternalPackages()
      ignoreGeneratedCode()
    }
  }
  structure {
    bundle("agp") {
      primary("com.android.tools.build:gradle")
      includeGroup("com.android.tools.build")
      includeDependency("com.google.code.findbugs:jsr305")
    }
  }
}
