/*
 * Copyright (C) 2024 Slack Technologies, LLC
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
package slack.gradle.develocity

import com.gradle.develocity.agent.gradle.adapters.BuildResultAdapter
import com.gradle.develocity.agent.gradle.adapters.BuildScanAdapter
import com.gradle.develocity.agent.gradle.adapters.BuildScanCaptureAdapter
import com.gradle.develocity.agent.gradle.adapters.BuildScanObfuscationAdapter
import com.gradle.develocity.agent.gradle.adapters.PublishedBuildScanAdapter
import com.gradle.develocity.agent.gradle.adapters.develocity.DevelocityConfigurationAdapter
import com.gradle.develocity.agent.gradle.adapters.enterprise.GradleEnterpriseExtensionAdapter
import org.gradle.api.Action
import org.gradle.api.Project

/*
 * Adapted from https://github.com/runningcode/gradle-doctor/blob/master/doctor-plugin/src/main/java/com/osacky/doctor/DevelocityApiFinder.kt
 */

internal fun findAdapter(project: Project): BuildScanAdapter {
  if (project.rootProject.extensions.findByName("develocity") != null) {
    return DevelocityConfigurationAdapter(project.rootProject.extensions.getByName("develocity"))
      .buildScan
  } else if (project.rootProject.extensions.findByName("gradleEnterprise") != null) {
    return GradleEnterpriseExtensionAdapter(
        project.rootProject.extensions.getByName("gradleEnterprise")
      )
      .buildScan
  }
  return NoOpBuildScanAdapter()
}

internal class NoOpBuildScanAdapter : BuildScanAdapter {
  override fun background(p0: Action<in BuildScanAdapter>?) {}

  override fun tag(p0: String?) {}

  override fun value(p0: String?, p1: String?) {}

  override fun link(p0: String?, p1: String?) {}

  override fun buildFinished(p0: Action<in BuildResultAdapter>?) {}

  override fun buildScanPublished(p0: Action<in PublishedBuildScanAdapter>?) {}

  override fun setTermsOfUseUrl(p0: String?) {}

  override fun getTermsOfUseUrl(): String? {
    throw UnsupportedOperationException("not implemented")
  }

  override fun setTermsOfUseAgree(p0: String?) {}

  override fun getTermsOfUseAgree(): String? {
    throw UnsupportedOperationException("not implemented")
  }

  override fun setUploadInBackground(p0: Boolean) {}

  override fun isUploadInBackground(): Boolean {
    throw UnsupportedOperationException("not implemented")
  }

  override fun publishAlways() {}

  override fun publishAlwaysIf(p0: Boolean) {}

  override fun publishOnFailure() {}

  override fun publishOnFailureIf(p0: Boolean) {}

  override fun getObfuscation(): BuildScanObfuscationAdapter? {
    throw UnsupportedOperationException("not implemented")
  }

  override fun obfuscation(p0: Action<in BuildScanObfuscationAdapter>?) {}

  override fun getCapture(): BuildScanCaptureAdapter? {
    throw UnsupportedOperationException("not implemented")
  }

  override fun capture(p0: Action<in BuildScanCaptureAdapter>?) {
    throw UnsupportedOperationException("not implemented")
  }
}
