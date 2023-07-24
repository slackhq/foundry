import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("java")
  kotlin("jvm")
  alias(libs.plugins.intellij)
}

group = "com.slack.sgp.intellij"

version = "1.0-SNAPSHOT"

repositories {
  mavenCentral()
  maven(url = "https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}

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

  runIdeForUiTests { systemProperty("robot-server.port", "8082") }

  downloadRobotServerPlugin { "0.11.19" }

  test { useJUnitPlatform() }
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
  testImplementation(libs.okhttp)
  testImplementation("com.intellij.remoterobot:remote-robot:0.11.19")
  testImplementation("com.intellij.remoterobot:remote-fixtures:0.11.19")
  testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.3")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.9.3")

  // Logging Network Calls
  testImplementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

  // Video Recording
  implementation("com.automation-remarks:video-recorder-junit5:2.0")
}
