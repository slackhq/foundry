import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("java")
  kotlin("jvm")
  alias(libs.plugins.intellij)
}

group = "com.slack.sgp.intellij"

version = "1.0-SNAPSHOT"

repositories { mavenCentral() }

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
  version.set("2022.2.5")
  type.set("IC") // Target IDE Platform

  plugins.add("org.intellij.plugins.markdown")
}

tasks {
  patchPluginXml {
    sinceBuild.set("222")
    untilBuild.set("232.*")
  }

  signPlugin {
    certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
    privateKey.set(System.getenv("PRIVATE_KEY"))
    password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
  }

  publishPlugin { token.set(System.getenv("PUBLISH_TOKEN")) }
}

// region Version.kt template for setting the project version in the build
sourceSets { main { java.srcDir("$buildDir/generated/sources/version-templates/kotlin/main") } }

val copyVersionTemplatesProvider =
  tasks.register<Copy>("copySkateVersionTemplates") {
    inputs.property("version", project.property("VERSION_NAME"))
    from(project.layout.projectDirectory.dir("version-templates"))
    into(project.layout.buildDirectory.dir("generated/sources/version-templates/kotlin/main"))
    expand(
      mapOf(
        "projectVersion" to project.property("VERSION_NAME").toString(),
        "bugsnagKey" to project.findProperty("BUGSNAG_KEY")?.toString().orEmpty(),
        "gitSha" to project.findProperty("GIT_SHA")?.toString().orEmpty(),
      )
    )
    filteringCharset = "UTF-8"
  }

tasks.withType<KotlinCompile>().configureEach { dependsOn(copyVersionTemplatesProvider) }

tasks
  .matching { it.name == "kotlinSourcesJar" }
  .configureEach { dependsOn(copyVersionTemplatesProvider) }
// endregion

dependencies {
  implementation(libs.bugsnag)
  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
