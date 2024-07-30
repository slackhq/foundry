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
dependencyResolutionManagement {
  versionCatalogs {
    if (System.getenv("DEP_OVERRIDES") == "true") {
      val overrides = System.getenv().filterKeys { it.startsWith("DEP_OVERRIDE_") }
      maybeCreate("libs").apply {
        for ((key, value) in overrides) {
          val catalogKey = key.removePrefix("DEP_OVERRIDE_").lowercase()
          println("Overriding $catalogKey with $value")
          version(catalogKey, value)
        }
      }
    }
  }

  // Non-delegate APIs are annoyingly not public so we have to use withGroovyBuilder
  fun hasProperty(key: String): Boolean {
    return settings.withGroovyBuilder { "hasProperty"(key) as Boolean }
  }

  repositories {
    // Repos are declared roughly in order of likely to hit.

    // Snapshots/local go first in order to pre-empty other repos that may contain unscrupulous
    // snapshots.
    if (hasProperty("slack.gradle.config.enableSnapshots")) {
      maven("https://oss.sonatype.org/content/repositories/snapshots")
      maven("https://androidx.dev/snapshots/latest/artifacts/repository")
      maven("https://oss.jfrog.org/libs-snapshot")
    }

    if (hasProperty("slack.gradle.config.enableMavenLocal")) {
      mavenLocal()
    }

    mavenCentral()

    google()

    // Kotlin bootstrap repository, useful for testing against Kotlin dev builds. Usually only
    // tested on CI shadow jobs
    // https://kotlinlang.slack.com/archives/C0KLZSCHF/p1616514468003200?thread_ts=1616509748.001400&cid=C0KLZSCHF
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap") {
      name = "Kotlin-Bootstrap"
      content {
        // this repository *only* contains Kotlin artifacts (don't try others here)
        includeGroupByRegex("org\\.jetbrains.*")
      }
    }

    maven("https://packages.jetbrains.team/maven/p/kpm/public/")
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")

    exclusiveContent {
      forRepository {
        // For R8/D8 releases
        maven("https://storage.googleapis.com/r8-releases/raw")
      }
      filter { includeModule("com.android.tools", "r8") }
    }

    // ExclusiveContent is used here because this proxies jcenter under the hood!
    exclusiveContent {
      forRepository(::gradlePluginPortal)
      filter {
        includeModule("com.github.ben-manes", "gradle-versions-plugin")
        includeModule("com.gradle", "develocity-gradle-plugin")
        includeModule("org.gradle", "test-retry-gradle-plugin")
        includeModule("gradle.plugin.org.gradle.android", "android-cache-fix-gradle-plugin")
        includeModule("net.ltgt.gradle", "gradle-errorprone-plugin")
        includeModule("net.ltgt.gradle", "gradle-nullaway-plugin")
        includeModule("com.jraska.module.graph.assertion", "plugin")
      }
    }
  }
}

pluginManagement {
  // Non-delegate APIs are annoyingly not public so we have to use withGroovyBuilder
  fun hasProperty(key: String): Boolean {
    return settings.withGroovyBuilder { "hasProperty"(key) as Boolean }
  }

  repositories {
    // Repos are declared roughly in order of likely to hit.

    // Snapshots/local go first in order to pre-empty other repos that may contain unscrupulous
    // snapshots.
    if (hasProperty("slack.gradle.config.enableSnapshots")) {
      maven("https://oss.sonatype.org/content/repositories/snapshots")
      maven("https://androidx.dev/snapshots/latest/artifacts/repository")
      maven("https://oss.jfrog.org/libs-snapshot")
    }

    if (hasProperty("slack.gradle.config.enableMavenLocal")) {
      mavenLocal()
    }

    mavenCentral()

    google()

    // Kotlin bootstrap repository, useful for testing against Kotlin dev builds. Usually only
    // tested on CI shadow jobs
    // https://kotlinlang.slack.com/archives/C0KLZSCHF/p1616514468003200?thread_ts=1616509748.001400&cid=C0KLZSCHF
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap") {
      name = "Kotlin-Bootstrap"
      content {
        // this repository *only* contains Kotlin artifacts (don't try others here)
        includeGroupByRegex("org\\.jetbrains.*")
      }
    }

    maven("https://packages.jetbrains.team/maven/p/kpm/public/")
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")

    // Gradle's plugin portal proxies jcenter, which we don't want. To avoid this, we specify
    // exactly which dependencies to pull from here.
    exclusiveContent {
      forRepository(::gradlePluginPortal)
      filter {
        includeModule("com.github.gmazzo.buildconfig", "plugin")
        includeModule(
          "com.github.gmazzo.buildconfig",
          "com.github.gmazzo.buildconfig.gradle.plugin",
        )
        includeModule("com.github.ben-manes", "gradle-versions-plugin")
        includeModule(
          "com.github.ben-manes.versions",
          "com.github.ben-manes.versions.gradle.plugin",
        )
        includeModule("com.gradle", "develocity-gradle-plugin")
        includeModule("com.gradle.develocity", "com.gradle.develocity.gradle.plugin")
        includeModule("com.diffplug.spotless", "com.diffplug.spotless.gradle.plugin")
        includeModule("io.gitlab.arturbosch.detekt", "io.gitlab.arturbosch.detekt.gradle.plugin")
        includeModule("org.gradle.kotlin.kotlin-dsl", "org.gradle.kotlin.kotlin-dsl.gradle.plugin")
        includeModule("org.gradle.kotlin", "gradle-kotlin-dsl-plugins")
        includeModule("com.autonomousapps", "plugin-best-practices-plugin")
        includeModule(
          "com.autonomousapps.plugin-best-practices-plugin",
          "com.autonomousapps.plugin-best-practices-plugin.gradle.plugin",
        )
        includeModule("org.jetbrains.intellij", "org.jetbrains.intellij.gradle.plugin")
        includeModule("org.jetbrains.intellij.plugins", "gradle-intellij-plugin")
        includeModule("gradle.plugin.org.jetbrains.gradle.plugin.idea-ext", "gradle-idea-ext")
        includeGroup("dev.bmac.intellij.plugins")
        includeGroup("dev.bmac.intellij.plugin-uploader")
      }
    }
  }
}

plugins { id("com.gradle.develocity") version "3.17.2" }

val VERSION_NAME: String by extra.properties

develocity {
  buildScan {
    termsOfUseUrl.set("https://gradle.com/terms-of-service")
    termsOfUseAgree.set("yes")

    tag(if (System.getenv("CI").isNullOrBlank()) "Local" else "CI")
    tag(VERSION_NAME)
  }
}

rootProject.name = "slack-gradle-plugin"

// Please keep these in alphabetical order!
include(
  ":agp-handlers:agp-handler-api",
  ":skippy",
  ":sgp-common",
  ":skate-plugin",
  ":skate-plugin:artifactory-authenticator",
  ":skate-plugin:compose-playground",
  ":skate-plugin:project-gen",
  ":slack-plugin",
  ":tracing",
)

// https://docs.gradle.org/5.6/userguide/groovy_plugin.html#sec:groovy_compilation_avoidance
enableFeaturePreview("GROOVY_COMPILATION_AVOIDANCE")

// https://docs.gradle.org/current/userguide/declaring_dependencies.html#sec:type-safe-project-accessors
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
