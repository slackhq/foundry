/*
 * Copyright (C) 2023 Slack Technologies, LLC
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
package com.slack.sgp.intellij

import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.slack.sgp.intellij.ui.SkateConfigUI

class SkateConfig(private val project: Project) :
  BoundSearchableConfigurable(
    displayName = SkateBundle.message("skate.configuration.title"),
    helpTopic = SkateBundle.message("skate.configuration.title"),
    _id = "com.slack.skate.config",
  ) {
  private val settings = project.service<SkatePluginSettings>()

  override fun createPanel(): DialogPanel {
    return SkateConfigUI(settings, project).createPanel()
  }
}
