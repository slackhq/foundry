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
import com.diffplug.gradle.spotless.KotlinExtension
import com.diffplug.gradle.spotless.SpotlessExtension

// Regression coverage runs Spotless under isolated projects. See
// https://github.com/diffplug/spotless/issues/1213 for the original issue.
plugins { id("com.diffplug.spotless") }

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val ktfmtVersion = catalog.findVersion("ktfmt").get().toString()

val externalFiles =
  listOf("SkateErrorHandler", "MemoizedSequence", "Publisher", "Resolver").map { "src/**/$it.kt" }

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
    licenseHeaderFile(rootDir.resolve("spotless/spotless.kt"))
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
      rootDir.resolve("spotless/spotless.kt"),
      "(import|plugins|buildscript|dependencies|pluginManagement|dependencyResolutionManagement)",
    )
  }
}
