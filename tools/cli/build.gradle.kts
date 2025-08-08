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
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.dokka)
  alias(libs.plugins.detekt)
  alias(libs.plugins.lint)
  alias(libs.plugins.mavenPublish)
  alias(libs.plugins.spotless)
  alias(libs.plugins.moshix)
  alias(libs.plugins.kotlin.plugin.serialization)
  alias(libs.plugins.ksp)
}

kotlin {
  @OptIn(ExperimentalAbiValidation::class) abiValidation { enabled.set(true) }
  compilerOptions {
    optIn.addAll("kotlin.ExperimentalStdlibApi", "kotlinx.coroutines.ExperimentalCoroutinesApi")
  }
}

lint { baseline = file("lint-baseline.xml") }

moshi { enableSealed.set(true) }

// We have a couple flaky tests on CI right now
if (System.getenv("CI") != null) {
  tasks.test {
    develocity.testRetry {
      maxRetries.set(2)
      maxFailures.set(20)
      failOnPassedAfterRetry.set(false)
    }
  }
}

dependencies {
  api(libs.clikt)

  implementation(project(":tools:foundry-common"))
  implementation(libs.autoService.annotations)
  implementation(libs.bugsnag)
  implementation(libs.kotlin.reflect)
  implementation(libs.kotlinShell)
  implementation(libs.kotlinx.serialization.core)
  implementation(libs.moshi)
  implementation(libs.okhttp)
  implementation(libs.okio)
  implementation(libs.sarif4k)
  // To silence this stupid log https://www.slf4j.org/codes.html#StaticLoggerBinder
  implementation(libs.slf4jNop)
  implementation(libs.tikxml.htmlEscape)
  implementation(libs.xmlutil.serialization)

  testImplementation(libs.junit)
  testImplementation(libs.kaml)
  testImplementation(libs.truth)

  ksp(libs.autoService.ksp)
}
