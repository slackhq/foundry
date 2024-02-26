/*
 * Copyright (C) 2023 Slack Technologies, LLC
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
plugins {
  kotlin("jvm")
  alias(libs.plugins.ksp)
  alias(libs.plugins.mavenPublish)
  alias(libs.plugins.buildConfig)
  alias(libs.plugins.lint)
}

buildConfig {
  packageName("slack.gradle.agphandler.v82")
  buildConfigField("String", "AGP_VERSION", libs.versions.agp.map { "\"$it\"" })
  useKotlinOutput {
    topLevelConstants = true
    internalVisibility = true
  }
}

dependencies {
  ksp(libs.autoService.ksp)

  lintChecks(libs.gradleLints)

  api(projects.agpHandlers.agpHandlerApi)

  implementation(libs.autoService.annotations)

  compileOnly(gradleApi())
  compileOnly(libs.agp)
}
