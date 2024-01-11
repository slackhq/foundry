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
plugins {
  kotlin("jvm")
  alias(libs.plugins.moshix)
  alias(libs.plugins.mavenPublish)
}

dependencies {
  api(platform(libs.coroutines.bom))

  implementation(libs.clikt)
  implementation(libs.coroutines.core)
  implementation(libs.gradlePlugins.graphAssert) { because("To use in Gradle graphing APIs.") }
  implementation(libs.kotlinCliUtil)
  implementation(libs.moshi)
  implementation(libs.okio)
  implementation(projects.sgpCommon)

  testImplementation(platform(libs.coroutines.bom))
  testImplementation(libs.coroutines.test)
  testImplementation(libs.junit)
  testImplementation(libs.okio.fakefilesystem)
  testImplementation(libs.truth)
}
