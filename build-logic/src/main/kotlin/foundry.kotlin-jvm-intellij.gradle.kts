/*
 * Copyright (C) 2026 Slack Technologies, LLC
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
import foundry.buildlogic.configureKotlinJvmConvention
import foundry.buildlogic.parseJdkVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.squareup.sort-dependencies")
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val jvmTargetVersion =
  JvmTarget.fromTarget(catalog.findVersion("jvmTargetIdea").get().requiredVersion)
val jdkVersion = parseJdkVersion(catalog.findVersion("jdk").get().requiredVersion)

configureKotlinJvmConvention(
  jvmTargetVersion = jvmTargetVersion,
  jdkVersion = jdkVersion,
  // https://plugins.jetbrains.com/docs/intellij/using-kotlin.html#kotlin-standard-library
  kotlinLanguageVersion = KotlinVersion.KOTLIN_2_2,
  // IntelliJ forces older Kotlin, which results in warnings.
  allWarningsAsErrors = false,
  // IntelliJ plugins intentionally do not use explicit API mode.
  useExplicitApi = false,
)
