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
package slack.gradle.tasks

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.system.measureTimeMillis
import okio.buffer
import okio.source
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.NodeList

@Suppress("UnstableApiUsage")
@CacheableTask
internal abstract class CheckManifestPermissionsTask : DefaultTask() {

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
      if (added.isNotEmpty()) {
        throw PermissionAllowlistException(
          "New permission(s) detected! If this is intentional, " +
            "please add them to $allowlistFile and update your PR (a code owners group will be " +
            "added for review). Added permissions: $added"
        )
      }

      val removed = allowlist - permissions
      if (removed.isNotEmpty()) {
        throw PermissionAllowlistException(
          "Removed permission(s) detected! If this is " +
            "intentional, please remove them to $allowlistFile and update your PR (a code owners " +
            "group will be added for review). Removed permissions: $removed"
        )
      }

      manifestFile.copyTo(outputFile.asFile.get(), overwrite = true)
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

internal class PermissionAllowlistException(message: String) : RuntimeException(message)
