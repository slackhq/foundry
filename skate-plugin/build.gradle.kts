import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  java
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.intellij)
  alias(libs.plugins.pluginUploader)
}

group = "com.slack.intellij"

repositories { mavenCentral() }

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
  version.set("2022.2.5")
  type.set("IC") // Target IDE Platform

  plugins.add("org.intellij.plugins.markdown")
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
        "bugsnagKey" to project.findProperty("SgpIntellijBugsnagKey")?.toString().orEmpty(),
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
