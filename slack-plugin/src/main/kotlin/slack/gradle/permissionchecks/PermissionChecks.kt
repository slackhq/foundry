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
package slack.gradle.permissionchecks

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import java.io.File
import java.util.LinkedHashSet
import java.util.Locale
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import slack.gradle.agp.PermissionAllowlistConfigurer
import slack.gradle.agp.VariantConfiguration
import slack.gradle.configure
import slack.gradle.tasks.CheckManifestPermissionsTask

// TODO simplify this now that it's no longer in AgpHandler
internal object PermissionChecks {

  private class DefaultPermissionAllowlistConfigurer(variant: ApplicationVariant) :
    PermissionAllowlistConfigurer, VariantConfiguration by DefaultVariantConfiguration(variant) {
    var file: File? = null

    override fun setAllowlistFile(file: File) {
      this.file = file
    }
  }

  private class DefaultVariantConfiguration(private val variant: ApplicationVariant) :
    VariantConfiguration {
    override val buildType
      get() = variant.buildType

    override val flavors: List<Pair<String, String>>
      get() = variant.productFlavors

    override val name: String
      get() = variant.name
  }

  internal fun configure(
    project: Project,
    allowListActionGetter: () -> Action<PermissionAllowlistConfigurer>?,
    createTask:
      (taskName: String, file: File, allowListProvider: Provider<Set<String>>) -> TaskProvider<
          out CheckManifestPermissionsTask
        >
  ) {
    project.configure<ApplicationAndroidComponentsExtension> {
      onVariants { variant ->
        allowListActionGetter()?.let { allowListAction ->
          val configurer = DefaultPermissionAllowlistConfigurer(variant)
          allowListAction.execute(configurer)
          configurer.file?.let { file ->
            // We've got a allowlist! Wire up a task for each output manifest
            // Cache the allowlist parse, it's shared by however many outputs there are below
            val cachedAllowlist = lazy { file.readLines().mapTo(LinkedHashSet(), String::trim) }
            val allowlist: Provider<Set<String>> = project.provider { cachedAllowlist.value }
            val capitalizedName =
              variant.name.replaceFirstChar {
                if (it.isLowerCase()) {
                  it.titlecase(Locale.US)
                } else {
                  it.toString()
                }
              }
            val taskName = "check${capitalizedName}PermissionsAllowlist"
            @Suppress("UnstableApiUsage")
            val checkPermissionsAllowlist = createTask(taskName, file, allowlist)
            variant.artifacts
              .use(checkPermissionsAllowlist)
              .wiredWithFiles(
                taskInput = CheckManifestPermissionsTask::inputFile,
                taskOutput = CheckManifestPermissionsTask::outputFile
              )
              .toTransform(SingleArtifact.MERGED_MANIFEST)
          }
        }
      }
    }
  }
}
