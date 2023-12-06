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
package slack.gradle

import java.io.IOException
import java.net.URLEncoder
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.configuration.BuildFeatures
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.testing.Test
import slack.gradle.util.gitExecProvider
import slack.gradle.util.gitVersionProvider

private val GITHUB_ORIGIN_REGEX = Regex("(.*)github\\.com[/|:](.*)")

internal fun BuildFeatures.reportTo(scanApi: ScanApi) {
  scanApi.value("bf-configuration-cache-requested", configurationCache.requested.getOrElse(false))
  scanApi.value("bf-configuration-cache-active", configurationCache.active.getOrElse(false))
  scanApi.value("bf-isolated-projects-requested", isolatedProjects.requested.getOrElse(false))
  scanApi.value("bf-isolated-projects-active", isolatedProjects.active.getOrElse(false))
}

internal fun Project.configureBuildScanMetadata(scanApi: ScanApi) {
  if (invokedFromIde) {
    scanApi.tag("ide")
    if (isSyncing) {
      scanApi.tag("studio-sync")
    }
  }
  scanApi.value("java-version", JavaVersion.current().toString())
  scanApi.value("Architecture", System.getProperty("os.arch"))
  val isCi = isCi

  scanApi.tagOs()
  scanApi.tagIde(project, isCi)
  scanApi.tagCiOrLocal(isCi)
  if (isCi) {
    scanApi.addCiMetadata(project)
  }
  scanApi.addGitMetadata(project)
  scanApi.addTestParallelization(project)
  scanApi.addGradleEnterpriseVersion()
}

private fun ScanApi.tagOs() {
  tag(System.getProperty("os.name"))
}

private fun ScanApi.tagIde(project: Project, isCi: Boolean) {
  if (project.hasProperty("android.injected.invoked.from.ide")) {
    tag("Android Studio")
    project.findProperty("android.studio.version")?.let {
      value("Android Studio version", it.toString())
    }
  } else if (System.getProperty("idea.version") != null) {
    tag("IntelliJ IDEA")
  } else if (!isCi) {
    tag("Cmd Line")
  }
}

private fun ScanApi.tagCiOrLocal(isCi: Boolean) {
  tag(if (isCi) "CI" else "LOCAL")
}

private fun ScanApi.addCiMetadata(project: Project) {
  if (project.isActionsCi) {
    if (System.getenv("GITHUB_REPOSITORY") != null && System.getenv("GITHUB_RUN_ID") != null) {
      link(
        "GitHub Actions build",
        "https://github.com/${System.getenv("GITHUB_REPOSITORY")}/actions/runs/${System.getenv("GITHUB_RUN_ID")}"
      )
    }
    if (System.getenv("GITHUB_WORKFLOW") != null) {
      val workflowNameLabel = "GitHub workflow"
      val workflowName = System.getenv("GITHUB_WORKFLOW")
      value(workflowNameLabel, workflowName)
      addCustomLinkWithSearchTerms(
        "GitHub workflow build scans",
        mapOf(workflowNameLabel to workflowName)
      )
    }
  }
}

private fun ScanApi.addGitMetadata(project: Project) {
  val providers = project.providers
  background {
    if (!isGitInstalled(providers)) {
      return@background
    }

    val gitCommitId =
      providers.gitExecProvider("git", "rev-parse", "--short=8", "--verify", "HEAD").orNull
    val gitBranchName = providers.gitExecProvider("git", "rev-parse", "--abbrev-ref", "HEAD").orNull

    val gitStatus = providers.gitExecProvider("git", "status", "--porcelain").orNull

    if (gitCommitId != null) {
      val gitCommitIdLabel = "Git commit id"
      value(gitCommitIdLabel, gitCommitId)
      addCustomLinkWithSearchTerms(
        "Git commit id build scans",
        mapOf(gitCommitIdLabel to gitCommitId)
      )

      val originUrl =
        providers.gitExecProvider("git", "config", "--get", "remote.origin.url").orNull
      if (originUrl != null) {
        if ("github.com/" in originUrl || "github.com:" in originUrl) {
          GITHUB_ORIGIN_REGEX.find(originUrl)?.groups?.get(2)?.value?.removeSuffix(".git")?.let {
            repoPath ->
            link("Github Source", "https://github.com/$repoPath/tree/$gitCommitId")
          }
        }
      }
    }
    if (gitBranchName != null) {
      tag(gitBranchName)
      value("Git branch", gitBranchName)
    }
    if (gitStatus != null) {
      tag("Dirty")
      value("Git status", gitStatus)
    }
  }
}

internal fun ScanApi.addTestParallelization(project: Project) {
  project.tasks.withType(Test::class.java).configureEach {
    doFirst { value("$identityPath#maxParallelForks", maxParallelForks.toString()) }
  }
}

private fun ScanApi.addGradleEnterpriseVersion() {
  javaClass.classLoader
    .getResource("com.gradle.scan.plugin.internal.meta.buildAgentVersion.txt")
    ?.readText()
    ?.let { buildAgentVersion -> value("GE Gradle plugin version", buildAgentVersion) }
}

private fun ScanApi.addCustomLinkWithSearchTerms(title: String, search: Map<String, String>) {
  server?.let {
    val searchParams = customValueSearchParams(search)
    val url =
      "${it.appendIfMissing("/")}scans?$searchParams#selection.buildScanB=${urlEncode("{SCAN_ID}")}"
    link(title, url)
  }
}

private fun customValueSearchParams(search: Map<String, String>): String {
  return search.entries.joinToString("&") { (name, value) ->
    "search.names=${urlEncode(name)}&search.values=${urlEncode(value)}"
  }
}

private fun String.appendIfMissing(suffix: String): String {
  return if (endsWith(suffix)) this else this + suffix
}

private fun urlEncode(url: String): String {
  return URLEncoder.encode(url, Charsets.UTF_8.name())
}

private fun isGitInstalled(providers: ProviderFactory): Boolean {
  return try {
    providers.gitVersionProvider().isPresent
    true
  } catch (ignored: IOException) {
    false
  }
}
