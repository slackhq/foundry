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
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.samWithReceiver.gradle.SamWithReceiverExtension

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("org.jetbrains.kotlin.plugin.sam.with.receiver")
  id("com.squareup.sort-dependencies")
}

val jvmTargetVersion = JvmTarget.JVM_21
val jdkVersion = 23

extensions.configure<KotlinJvmProjectExtension> { explicitApi() }

extensions.configure<SamWithReceiverExtension> {
  annotation("org.gradle.api.HasImplicitReceiver")
}

extensions.configure<JavaPluginExtension> {
  toolchain { languageVersion.set(JavaLanguageVersion.of(jdkVersion)) }
}

tasks.withType<JavaCompile>().configureEach {
  options.release.set(jvmTargetVersion.target.toInt())
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
  compilerOptions {
    languageVersion.set(KotlinVersion.KOTLIN_2_2)
    apiVersion.set(KotlinVersion.KOTLIN_2_2)
    // Gradle forces older Kotlin, which results in warnings
    allWarningsAsErrors.set(false)

    check(this is KotlinJvmCompilerOptions)
    this.jvmTarget.set(jvmTargetVersion)
    jvmDefault.set(org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode.NO_COMPATIBILITY)
    freeCompilerArgs.addAll(
      // Required due to https://github.com/gradle/gradle/issues/24871
      "-Xsam-conversions=class",
      "-Xlambdas=class",
      "-Xenhance-type-parameter-types-to-def-not-null",
      "-Xjsr305=strict",
      "-Xassertions=jvm",
      "-Xemit-jvm-type-annotations",
      "-Xtype-enhancement-improvements-strict-mode",
      "-Xjspecify-annotations=strict",
      "-Xannotation-default-target=param-property",
      "-Xjdk-release=${jvmTargetVersion.target}",
    )
    optIn.addAll(
      "kotlin.contracts.ExperimentalContracts",
      "kotlin.experimental.ExperimentalTypeInference",
      "kotlin.ExperimentalStdlibApi",
      "kotlin.time.ExperimentalTime",
    )
  }
}
