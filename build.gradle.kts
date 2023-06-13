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
import com.diffplug.gradle.spotless.SpotlessExtension
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.util.internal.VersionNumber
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_6
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_8
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.samWithReceiver.gradle.SamWithReceiverExtension
import java.net.URI

buildscript {
  dependencies {
    // We have to declare this here in order for kotlin-facets to be generated in iml files
    // https://youtrack.jetbrains.com/issue/KT-36331
    classpath(kotlin("gradle-plugin", libs.versions.kotlin.get()))
    classpath(kotlin("sam-with-receiver", libs.versions.kotlin.get()))
    classpath(libs.markdown)
  }
}

plugins {
  alias(libs.plugins.detekt)
  alias(libs.plugins.spotless) apply false
  alias(libs.plugins.mavenPublish) apply false
  alias(libs.plugins.dokka) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.versionsPlugin)
  alias(libs.plugins.dependencyAnalysis)
  alias(libs.plugins.sortDependencies) apply false
}

configure<DetektExtension> {
  toolVersion = libs.versions.detekt.get()
  allRules = true
}

tasks.withType<Detekt>().configureEach {
  reports {
    html.required.set(true)
    xml.required.set(true)
    txt.required.set(true)
  }
}

val ktfmtVersion = libs.versions.ktfmt.get()

allprojects {
  apply(plugin = "com.diffplug.spotless")
  configure<SpotlessExtension> {
    format("misc") {
      target("*.md", ".gitignore")
      trimTrailingWhitespace()
      endWithNewline()
    }
    kotlin {
      target("src/**/*.kt")
      ktfmt(ktfmtVersion).googleStyle()
      trimTrailingWhitespace()
      endWithNewline()
      licenseHeaderFile(rootProject.file("spotless/spotless.kt"))
      targetExclude("**/spotless.kt", "**/Aliases.kt")
    }
    kotlinGradle {
      target("src/**/*.kts")
      ktfmt(ktfmtVersion).googleStyle()
      trimTrailingWhitespace()
      endWithNewline()
      licenseHeaderFile(
        rootProject.file("spotless/spotless.kt"),
        "(import|plugins|buildscript|dependencies|pluginManagement)"
      )
    }
  }
}

/**
 * These are magic shared versions that are used in both buildSrc's build file and
 * SlackDependencies. These are copied as a source into the main source set and templated for
 * replacement.
 */
data class KotlinBuildConfig(val kotlin: String) {
  private val kotlinVersion by lazy {
    val (major, minor, patch) = kotlin.substringBefore("-").split('.').map { it.toInt() }
    KotlinVersion(major, minor, patch)
  }

  // Left as a toe-hold for any future needs
  private val extraArgs = arrayOf<String>()

  /**
   * See more information about these in
   * - CommonCompilerArguments.kt
   * - K2JVMCompilerArguments.kt
   */
  val kotlinCompilerArgs: List<String> =
    listOf(
      "-progressive",
      "-opt-in=kotlin.contracts.ExperimentalContracts",
      "-opt-in=kotlin.experimental.ExperimentalTypeInference",
      "-opt-in=kotlin.ExperimentalStdlibApi",
      "-opt-in=kotlin.time.ExperimentalTime",
      "-Xproper-ieee754-comparisons",
      // Enhance not null annotated type parameter's types to definitely not null types (@NotNull T
      // => T & Any)
      "-Xenhance-type-parameter-types-to-def-not-null",
      // Use fast implementation on Jar FS. This may speed up compilation time, but currently it's
      // an experimental mode
      // TODO toe-hold but we can't use it yet because it emits a warning that fails with -Werror
      //  https://youtrack.jetbrains.com/issue/KT-54928
      //    "-Xuse-fast-jar-file-system",
      // Support inferring type arguments based on only self upper bounds of the corresponding type
      // parameters
      "-Xself-upper-bound-inference",
    ) + extraArgs

  /**
   * See more information about these in
   * - CommonCompilerArguments.kt
   * - K2JVMCompilerArguments.kt
   */
  val kotlinJvmCompilerArgs: List<String> =
    listOf(
      "-Xjsr305=strict",
      // Match JVM assertion behavior:
      // https://publicobject.com/2019/11/18/kotlins-assert-is-not-like-javas-assert/
      "-Xassertions=jvm",
      // Potentially useful for static analysis tools or annotation processors.
      "-Xemit-jvm-type-annotations",
      // Enable new jvm-default behavior
      // https://blog.jetbrains.com/kotlin/2020/07/kotlin-1-4-m3-generating-default-methods-in-interfaces/
      "-Xjvm-default=all",
      "-Xtype-enhancement-improvements-strict-mode",
      // https://kotlinlang.org/docs/whatsnew1520.html#support-for-jspecify-nullness-annotations
      "-Xjspecify-annotations=strict",
    )

  fun asTemplatesMap(): Map<String, String> {
    return mapOf(
      "kotlinCompilerArgs" to kotlinCompilerArgs.joinToString(", ") { "\"$it\"" },
      "kotlinJvmCompilerArgs" to kotlinJvmCompilerArgs.joinToString(", ") { "\"$it\"" },
      "kotlinVersion" to kotlin
    )
  }
}

val kotlinVersion = libs.versions.kotlin.get()
val kotlinBuildConfig = KotlinBuildConfig(kotlinVersion)

subprojects {
  // This is overly magic but necessary in order to plumb this
  // down to subprojects
  tasks
    .withType<Copy>()
    .matching { it.name == "copyVersionTemplates" }
    .configureEach {
      val templatesMap = kotlinBuildConfig.asTemplatesMap()
      inputs.property("buildversions", templatesMap.hashCode())
      expand(templatesMap)
    }

  pluginManager.withPlugin("java") {
    configure<JavaPluginExtension> {
      toolchain {
        languageVersion.set(
          JavaLanguageVersion.of(libs.versions.jdk.get().removeSuffix("-ea").toInt())
        )
      }
    }

    tasks.withType<JavaCompile>().configureEach { options.release.set(17) }
  }

  val isSkatePlugin = project.path == ":skate-plugin"
  pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    tasks.withType<KotlinCompile>().configureEach {
      compilerOptions {
        val kotlinVersion =
          if (isSkatePlugin) {
            KOTLIN_1_6
          } else {
            KOTLIN_1_8
          }
        languageVersion.set(kotlinVersion)
        apiVersion.set(kotlinVersion)

        if (!isSkatePlugin) {
          // Gradle forces a lower version of kotlin, which results in warnings that prevent use of
          // this sometimes. https://github.com/gradle/gradle/issues/16345
          allWarningsAsErrors.set(false)
          // TODO required due to https://github.com/gradle/gradle/issues/24871
          freeCompilerArgs.add("-Xsam-conversions=class")
        } else {
          allWarningsAsErrors.set(true)
        }
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
          kotlinBuildConfig.kotlinCompilerArgs
            // -progressive is useless when running on an older language version but new compiler
            // version. Both Gradle and IntelliJ plugins have this issue 🙃
            .filter { it != "-progressive" }
        )
        freeCompilerArgs.addAll(kotlinBuildConfig.kotlinJvmCompilerArgs)
      }
    }

    if (!isSkatePlugin) {
      extensions.configure<KotlinProjectExtension> { explicitApi() }
    }

    // Reimplement kotlin-dsl's application of this function for nice DSLs
    apply(plugin = "kotlin-sam-with-receiver")
    configure<SamWithReceiverExtension> { annotation("org.gradle.api.HasImplicitReceiver") }

    apply(plugin = "com.squareup.sort-dependencies")
  }

  tasks.withType<Detekt>().configureEach { jvmTarget = "17" }

  pluginManager.withPlugin("com.vanniktech.maven.publish") {
    apply(plugin = "org.jetbrains.dokka")

    tasks.withType<DokkaTask>().configureEach {
      outputDirectory.set(rootDir.resolve("../docs/0.x"))
      dokkaSourceSets.configureEach {
        skipDeprecated.set(true)
        // Gradle docs
        externalDocumentationLink {
          url.set(URI("https://docs.gradle.org/${gradle.gradleVersion}/javadoc/index.html").toURL())
        }
        // AGP docs
        externalDocumentationLink {
          val agpVersionNumber = VersionNumber.parse(libs.versions.agp.get()).baseVersion
          val simpleApi = "${agpVersionNumber.major}.${agpVersionNumber.minor}"
          packageListUrl.set(
            URI("https://developer.android.com/reference/tools/gradle-api/$simpleApi/package-list")
              .toURL()
          )
          url.set(
            URI("https://developer.android.com/reference/tools/gradle-api/$simpleApi/classes")
              .toURL()
          )
        }
      }
    }

    configure<MavenPublishBaseExtension> {
      publishToMavenCentral(automaticRelease = true)
      signAllPublications()
    }
  }
}

dependencyAnalysis {
  abi {
    exclusions {
      ignoreInternalPackages()
      ignoreGeneratedCode()
    }
  }
  this.dependencies {
    bundle("agp") {
      primary("com.android.tools.build:gradle")
      includeGroup("com.android.tools.build")
      includeDependency("com.google.code.findbugs:jsr305")
    }
  }
}
