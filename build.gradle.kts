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
import com.android.build.api.dsl.Lint
import com.diffplug.gradle.spotless.KotlinExtension
import com.diffplug.gradle.spotless.SpotlessExtension
import com.github.gmazzo.buildconfig.BuildConfigExtension
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import dev.bmac.gradle.intellij.GenerateBlockMapTask
import dev.bmac.gradle.intellij.PluginUploader
import dev.bmac.gradle.intellij.UploadPluginTask
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import java.net.URI
import okio.ByteString.Companion.encode
import org.gradle.util.internal.VersionNumber
import org.jetbrains.dokka.gradle.DokkaTaskPartial
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.tasks.BuildPluginTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.Companion.DEFAULT
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.samWithReceiver.gradle.SamWithReceiverExtension

plugins {
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.multiplatform) apply false
  alias(libs.plugins.kotlin.plugin.sam)
  alias(libs.plugins.detekt)
  alias(libs.plugins.spotless) apply false
  alias(libs.plugins.mavenPublish) apply false
  alias(libs.plugins.dokka)
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.versionsPlugin)
  alias(libs.plugins.dependencyAnalysis)
  alias(libs.plugins.sortDependencies) apply false
  alias(libs.plugins.intellij) apply false
  alias(libs.plugins.pluginUploader) apply false
  alias(libs.plugins.buildConfig) apply false
  alias(libs.plugins.lint) apply false
  alias(libs.plugins.wire) apply false
  alias(libs.plugins.binaryCompatibilityValidator)
}

apiValidation {
  // only :tools:cli is tracking this right now
  // Annoyingly this only uses simple names
  // https://github.com/Kotlin/binary-compatibility-validator/issues/257
  ignoredProjects +=
    listOf(
      "agp-handler-api",
      "foundry-gradle-plugin",
      "artifactory-authenticator",
      "compose",
      "playground",
      "skate",
      "foundry-common",
      "skippy",
      "tracing",
    )
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

val externalFiles =
  listOf("SkateErrorHandler", "MemoizedSequence", "Publisher", "Resolver").map { "src/**/$it.kt" }

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
      targetExclude(externalFiles)
      ktfmt(ktfmtVersion).googleStyle()
      trimTrailingWhitespace()
      endWithNewline()
      licenseHeaderFile(rootProject.file("spotless/spotless.kt"))
      targetExclude("**/spotless.kt", "**/Aliases.kt", *externalFiles.toTypedArray())
    }
    format("kotlinExternal", KotlinExtension::class.java) {
      target(externalFiles)
      ktfmt(ktfmtVersion).googleStyle()
      trimTrailingWhitespace()
      endWithNewline()
      targetExclude("**/spotless.kt", "**/Aliases.kt")
    }
    kotlinGradle {
      target("*.kts", "src/**/*.kts")
      ktfmt(ktfmtVersion).googleStyle()
      trimTrailingWhitespace()
      endWithNewline()
      licenseHeaderFile(
        rootProject.file("spotless/spotless.kt"),
        "(import|plugins|buildscript|dependencies|pluginManagement|dependencyResolutionManagement)",
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
}

tasks.dokkaHtmlMultiModule {
  outputDirectory.set(rootDir.resolve("docs/api/0.x"))
  includes.from(project.layout.projectDirectory.file("README.md"))
}

val kotlinVersion = libs.versions.kotlin.get()
val kotlinBuildConfig = KotlinBuildConfig(kotlinVersion)

val jvmTargetVersion = libs.versions.jvmTarget.map(JvmTarget::fromTarget)

subprojects {
  project.pluginManager.withPlugin("com.github.gmazzo.buildconfig") {
    configure<BuildConfigExtension> {
      buildConfigField("String", "KOTLIN_VERSION", "\"$kotlinVersion\"")
    }
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

  val isForIntelliJPlugin =
    project.hasProperty("INTELLIJ_PLUGIN") || project.path.startsWith(":platforms:intellij")
  val isForGradle =
    project.hasProperty("GRADLE_PLUGIN") || project.path.startsWith(":platforms:gradle")
  pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    extensions.configure<KotlinJvmProjectExtension> {
      if (!isForIntelliJPlugin) {
        explicitApi()
      }
    }

    // Reimplement kotlin-dsl's application of this function for nice DSLs
    if (isForGradle) {
      apply(plugin = "kotlin-sam-with-receiver")
      configure<SamWithReceiverExtension> { annotation("org.gradle.api.HasImplicitReceiver") }
    }

    apply(plugin = "com.squareup.sort-dependencies")
  }

  plugins.withType<KotlinBasePlugin>().configureEach {
    tasks.withType<KotlinCompile>().configureEach {
      compilerOptions {
        val kotlinVersion =
          if (isForIntelliJPlugin) {
            KOTLIN_1_9
          } else if (isForGradle) {
            KOTLIN_1_9
          } else {
            DEFAULT
          }
        languageVersion.set(kotlinVersion)
        apiVersion.set(kotlinVersion)

        if (kotlinVersion != DEFAULT) {
          // Gradle/IntelliJ forces a lower version of kotlin, which results in warnings that
          // prevent use of this sometimes.
          // https://github.com/gradle/gradle/issues/16345
          allWarningsAsErrors.set(false)
        } else {
          allWarningsAsErrors.set(true)
        }
        if (isForGradle) {
          // TODO required due to https://github.com/gradle/gradle/issues/24871
          freeCompilerArgs.add("-Xsam-conversions=class")
        }
        this.jvmTarget.set(jvmTargetVersion)
        freeCompilerArgs.addAll(
          // Enhance not null annotated type parameter's types to definitely not null types
          // (@NotNull T
          // => T & Any)
          "-Xenhance-type-parameter-types-to-def-not-null",
          // Use fast implementation on Jar FS. This may speed up compilation time, but currently
          // it's
          // an experimental mode
          // TODO toe-hold but we can't use it yet because it emits a warning that fails with
          // -Werror
          //  https://youtrack.jetbrains.com/issue/KT-54928
          //    "-Xuse-fast-jar-file-system",
          // Support inferring type arguments based on only self upper bounds of the corresponding
          // type
          // parameters
          "-Xself-upper-bound-inference",
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
        // https://jakewharton.com/kotlins-jdk-release-compatibility-flag/
        freeCompilerArgs.add(jvmTargetVersion.map { "-Xjdk-release=${it.target}" })
        optIn.addAll(
          "kotlin.contracts.ExperimentalContracts",
          "kotlin.experimental.ExperimentalTypeInference",
          "kotlin.ExperimentalStdlibApi",
          "kotlin.time.ExperimentalTime",
        )
      }
    }

    tasks.withType<Detekt>().configureEach { this.jvmTarget = jvmTargetVersion.get().target }
  }

  pluginManager.withPlugin("com.vanniktech.maven.publish") {
    apply(plugin = "org.jetbrains.dokka")

    tasks.withType<DokkaTaskPartial>().configureEach {
      outputDirectory.set(layout.buildDirectory.dir("docs/partial"))
      dokkaSourceSets.configureEach {
        val readMeProvider = project.layout.projectDirectory.file("README.md")
        if (readMeProvider.asFile.exists()) {
          includes.from(readMeProvider)
        }
        skipDeprecated.set(true)
        if (isForGradle) {
          // Gradle docs
          externalDocumentationLink {
            url.set(
              URI("https://docs.gradle.org/${gradle.gradleVersion}/javadoc/index.html").toURL()
            )
          }
          // AGP docs
          externalDocumentationLink {
            val agpVersionNumber = VersionNumber.parse(libs.versions.agp.get()).baseVersion
            val simpleApi = "${agpVersionNumber.major}.${agpVersionNumber.minor}"
            packageListUrl.set(
              URI(
                  "https://developer.android.com/reference/tools/gradle-api/$simpleApi/package-list"
                )
                .toURL()
            )
            url.set(
              URI("https://developer.android.com/reference/tools/gradle-api/$simpleApi/classes")
                .toURL()
            )
          }
        }
        sourceLink {
          localDirectory.set(layout.projectDirectory.dir("src").asFile)
          val relPath = rootProject.projectDir.toPath().relativize(projectDir.toPath())
          remoteUrl.set(
            providers.gradleProperty("POM_SCM_URL").map { scmUrl ->
              URI("$scmUrl/tree/main/$relPath/src").toURL()
            }
          )
          remoteLineSuffix.set("#L")
        }
      }
    }

    configure<MavenPublishBaseExtension> {
      publishToMavenCentral(automaticRelease = true)
      signAllPublications()
    }
  }

  if (isForIntelliJPlugin) {
    project.pluginManager.withPlugin("org.jetbrains.intellij.platform") {
      data class PluginDetails(
        val pluginId: String,
        val name: String,
        val description: String,
        val version: String,
        val sinceBuild: String,
        val urlSuffix: String,
      )

      val pluginDetails =
        PluginDetails(
          pluginId = property("PLUGIN_ID").toString(),
          name = property("PLUGIN_NAME").toString(),
          description = property("PLUGIN_DESCRIPTION").toString(),
          version = property("VERSION_NAME").toString(),
          sinceBuild = property("PLUGIN_SINCE_BUILD").toString(),
          urlSuffix = property("ARTIFACTORY_URL_SUFFIX").toString(),
        )

      configure<IntelliJPlatformExtension> {
        pluginConfiguration {
          id.set(pluginDetails.pluginId)
          name.set(pluginDetails.name)
          version.set(pluginDetails.version)
          description.set(pluginDetails.description)
          ideaVersion {
            sinceBuild.set(pluginDetails.sinceBuild)
            untilBuild.set(project.provider { null })
          }
        }
      }
      project.dependencies {
        configure<IntelliJPlatformDependenciesExtension> { intellijIdeaCommunity("2024.1.2") }
      }

      if (hasProperty("SgpIntellijArtifactoryBaseUrl")) {
        pluginManager.apply(libs.plugins.pluginUploader.get().pluginId)
        val archive = project.tasks.named<BuildPluginTask>("buildPlugin").flatMap { it.archiveFile }
        val blockMapTask =
          tasks.named<GenerateBlockMapTask>(GenerateBlockMapTask.TASK_NAME) {
            notCompatibleWithConfigurationCache(
              "Blockmap generation is not compatible with the configuration cache"
            )
            file.set(archive)
            blockmapFile.set(
              project.layout.buildDirectory.file(
                "blockmap${GenerateBlockMapTask.BLOCKMAP_FILE_SUFFIX}"
              )
            )
            blockmapHashFile.set(
              project.layout.buildDirectory.file("blockmap${GenerateBlockMapTask.HASH_FILE_SUFFIX}")
            )
          }

        // Get the plugin distribution file from the signPlugin task provided from the
        // gradle-intellij-plugin
        tasks.register<UploadPluginTask>("uploadPluginToArtifactory") {
          notCompatibleWithConfigurationCache(
            "UploadPluginTask is not compatible with the configuration cache"
          )
          dependsOn(blockMapTask)
          url.set(
            providers.gradleProperty("SgpIntellijArtifactoryBaseUrl").map { baseUrl ->
              "$baseUrl/${pluginDetails.urlSuffix}"
            }
          )
          pluginName.set(pluginDetails.name)
          file.set(archive)
          repoType.set(PluginUploader.RepoType.REST_PUT)
          pluginId.set(pluginDetails.pluginId)
          version.set(pluginDetails.version)
          pluginDescription.set(pluginDetails.description)
          val changeNotesFile = file("change-notes.html")
          if (changeNotesFile.exists()) {
            changeNotes.set(changeNotesFile.readText())
          }
          sinceBuild.set(pluginDetails.sinceBuild)
          authentication.set(
            // Sip the username and token together to create an appropriate encoded auth header
            providers.gradleProperty("SgpIntellijArtifactoryUsername").zip(
              providers.gradleProperty("SgpIntellijArtifactoryToken")
            ) { username, token ->
              "Basic ${"$username:$token".encode().base64()}"
            }
          )
        }
      }
    }
  }

  pluginManager.withPlugin("com.android.lint") {
    configure<Lint> {
      lintConfig = rootProject.layout.projectDirectory.file("config/lint/lint.xml").asFile
      baseline = project.layout.projectDirectory.file("lint-baseline.xml").asFile
    }
    project.dependencies.add("lintChecks", libs.slackLints.checks)
    val configToAdd =
      if (pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
        "jvmMainCompileOnly"
      } else {
        "compileOnly"
      }
    afterEvaluate { project.dependencies.add(configToAdd, libs.slackLints.annotations) }
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
