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
import java.util.LinkedHashSet
import java.util.Locale
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import slack.gradle.agp.PermissionAllowlistConfigurer
import slack.gradle.agp.VariantConfiguration
import slack.gradle.configure
import slack.gradle.newInstance
import slack.gradle.tasks.CheckManifestPermissionsTask

// TODO simplify this now that it's no longer in AgpHandler
internal object PermissionChecks {

  abstract class DefaultPermissionAllowlistConfigurer
  @Inject
  constructor(
    objects: ObjectFactory,
    variant: ApplicationVariant,
  ) : PermissionAllowlistConfigurer, VariantConfiguration by DefaultVariantConfiguration(variant) {
    override val allowListFile: RegularFileProperty = objects.fileProperty()
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
      (
        taskName: String, file: Provider<RegularFile>, allowListProvider: Provider<Set<String>>
      ) -> TaskProvider<out CheckManifestPermissionsTask>,
  ) {
    project.configure<ApplicationAndroidComponentsExtension> {
      val objects = project.objects
      onVariants { variant ->
        allowListActionGetter()?.let { allowListAction ->
          val configurer = objects.newInstance<DefaultPermissionAllowlistConfigurer>(variant)
          allowListAction.execute(configurer)
          if (configurer.allowListFile.isPresent) {
            // We've got an allowlist! Wire up a task for each output manifest
            val allowlist: Provider<Set<String>> =
              configurer.allowListFile.map {
                it.asFile.readLines().mapTo(LinkedHashSet(), String::trim)
              }
            val capitalizedName =
              variant.name.replaceFirstChar {
                if (it.isLowerCase()) {
                  it.titlecase(Locale.US)
                } else {
                  it.toString()
                }
              }
            val taskName = "check${capitalizedName}PermissionsAllowlist"
            val checkPermissionsAllowlist =
              createTask(taskName, configurer.allowListFile, allowlist)
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
