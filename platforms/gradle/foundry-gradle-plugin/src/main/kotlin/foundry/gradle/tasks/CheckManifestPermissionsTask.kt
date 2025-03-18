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
package foundry.gradle.tasks

import foundry.gradle.FoundryShared
import java.io.File
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.system.measureTimeMillis
import okio.buffer
import okio.source
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.Problems
import org.gradle.api.problems.Severity
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.NodeList

@CacheableTask
internal abstract class CheckManifestPermissionsTask @Inject constructor(problems: Problems) :
  DefaultTask() {

  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val inputFile: RegularFileProperty

  @get:OutputFile abstract val outputFile: RegularFileProperty

  /**
   * We track the file for error reporting, but the content is extracted and cached separately via
   * [permissionAllowlist].
   */
  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val permissionAllowlistFile: RegularFileProperty

  @get:Input abstract val permissionAllowlist: SetProperty<String>

  private val problemReporter = problems.reporter

  @TaskAction
  fun check() {
    measureTimeMillis {
        val manifestFile = inputFile.asFile.get()
        logger.debug("$LOG Using manifest at $manifestFile")

        val allowlistFile = permissionAllowlistFile.asFile.get()
        logger.debug("$LOG Using allowlist permissions at $manifestFile")

        val allowlist = permissionAllowlist.get()
        logger.debug("$LOG ${allowlist.size} allowlisted permissions: $allowlist")

        val permissions = parseXmlPermissions(manifestFile)
        logger.debug("$LOG ${permissions.size} parsed permissions: $permissions")

        val added = permissions - allowlist
        val removed = allowlist - permissions
        var exception: PermissionAllowlistException? = null
        var solution = ""
        if (added.isNotEmpty()) {
          exception = PermissionAllowlistException("New permission(s) detected!")
          solution =
            "If this is intentional, please add them to $allowlistFile and " +
              "update your PR (a code owners group will be added for review)." +
              "Added permissions: $added"
        } else if (removed.isNotEmpty()) {
          exception = PermissionAllowlistException("Removed permission(s) detected!")
          solution =
            "If this is intentional, please remove them to $allowlistFile and update " +
              "your PR (a code owners group will be added for review)." +
              "Removed permissions: $removed"
        }

        if (exception == null) {
          manifestFile.copyTo(outputFile.asFile.get(), overwrite = true)
        } else {
          val problemId =
            ProblemId.create(
              "permission-allowlist",
              "Manifest permission check failure",
              FoundryShared.PROBLEM_GROUP,
            )
          problemReporter.throwing(exception, problemId) {
            fileLocation(manifestFile.absolutePath)
            solution(solution)
            severity(Severity.ERROR)
          }
        }
      }
      .let { logger.debug("$LOG Manifest perm checks took $it ms") }
  }

  companion object {
    private const val LOG = "[CheckManifestPermissionsTask]"

    internal fun parseXmlPermissions(file: File): Set<String> {
      val builderFactory = DocumentBuilderFactory.newInstance()
      val docBuilder = builderFactory.newDocumentBuilder()
      val doc = docBuilder.parse(file.source().buffer().inputStream())
      val permissions = doc.getElementsByTagName("uses-permission")
      val permissions23 = doc.getElementsByTagName("uses-permission-sdk-23")
      return permissions.parsePermissionValues() + permissions23.parsePermissionValues()
    }

    private fun NodeList.parsePermissionValues(): Set<String> {
      val perms = mutableSetOf<String>()
      for (i in 0 until length) {
        val node = item(i)
        val attrs = node.attributes
        val perm = attrs.getNamedItem("android:name")
        if (perm != null) {
          perms.add(perm.nodeValue)
        }
      }
      return perms
    }
  }
}

internal class PermissionAllowlistException(message: String) : GradleException(message)
