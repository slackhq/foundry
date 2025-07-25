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
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.mavenPublish)
  alias(libs.plugins.lint)
}

dependencies {
  implementation(project(":tools:version-number"))

  compileOnly(gradleApi())
  compileOnly(libs.agp)

  testImplementation(gradleApi())
  testImplementation(libs.agp)
  testImplementation(libs.junit)
  testImplementation(libs.truth)

  lintChecks(libs.gradleLints)
}
