import com.jetbrains.plugin.structure.base.utils.exists
import java.nio.file.Paths
import java.util.Locale
import kotlin.io.path.readText

plugins {
  java
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.intellij)
  alias(libs.plugins.pluginUploader)
  alias(libs.plugins.buildConfig)
}

group = "com.slack.intellij"

repositories { mavenCentral() }

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
  plugins.add("org.intellij.plugins.markdown")
  plugins.add("org.jetbrains.plugins.terminal")
  plugins.add("org.jetbrains.kotlin")
}

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

buildConfig {
  packageName("com.slack.sgp.intellij")
  buildConfigField("String", "VERSION", "\"${project.property("VERSION_NAME")}\"")
  buildConfigField("String", "BUGSNAG_KEY", "\"${project.findProperty("SgpIntellijBugsnagKey")?.toString().orEmpty()}\"")
  buildConfigField("String", "GIT_SHA", provider { "\"${readGitRepoCommit().orEmpty()}\"" })
  useKotlinOutput {
    topLevelConstants = true
    internalVisibility = true
  }
}

dependencies {
  implementation(libs.bugsnag) { exclude(group = "org.slf4j") }
  implementation(libs.okhttp)
  implementation(libs.okhttp.loggingInterceptor)
  implementation(projects.tracing)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
