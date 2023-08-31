import com.jetbrains.plugin.structure.base.utils.exists
import java.nio.file.Paths
import java.util.Locale
import kotlin.io.path.readText
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
intellij { plugins.add("org.intellij.plugins.markdown") }

fun isGitHash(hash: String): Boolean {
  if (hash.length != 40) {
    return false
  }

  return hash.all { it in '0'..'9' || it in 'a'..'f' }
}

// Impl from https://gist.github.com/madisp/6d753bde19e278755ec2b69ccfc17114
fun readGitRepoCommit(): String? {
  try {
    val head = Paths.get("${rootProject.projectDir}/.git").resolve("HEAD")
    if (!head.exists()) {
      return null
    }

    val headContents = head.readText(Charsets.UTF_8).lowercase(Locale.US).trim()

    if (isGitHash(headContents)) {
      return headContents
    }

    if (!headContents.startsWith("ref:")) {
      return null
    }

    val headRef = headContents.removePrefix("ref:").trim()
    val headFile = Paths.get(".git").resolve(headRef)
    if (!headFile.exists()) {
      return null
    }

    return headFile.readText(Charsets.UTF_8).trim().takeIf { isGitHash(it) }
  } catch (e: Exception) {
    return null
  }
}

// region Version.kt template for setting the project version in the build
sourceSets {
  main { java.srcDir(layout.buildDirectory.dir("generated/sources/version-templates/kotlin/main")) }
}

val copyVersionTemplatesProvider =
  tasks.register<Copy>("copySkateVersionTemplates") {
    inputs.property("version", project.property("VERSION_NAME"))
    from(project.layout.projectDirectory.dir("version-templates"))
    into(project.layout.buildDirectory.dir("generated/sources/version-templates/kotlin/main"))
    expand(
      mapOf(
        "projectVersion" to project.property("VERSION_NAME").toString(),
        "bugsnagKey" to project.findProperty("SgpIntellijBugsnagKey")?.toString().orEmpty(),
        "gitSha" to readGitRepoCommit().orEmpty(),
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
  compileOnly(libs.kotlin.compiler.embeddable)
  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.kotlin.compiler.embeddable)
  testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.2")
  testImplementation("org.junit.jupiter:junit-jupiter-engine:5.7.2")
  testImplementation("io.mockk:mockk:1.13.7")
}
