package com.slack.sgp.intellij

import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.slack.sgp.intellij.ui.SkateConfigUI

class SkateConfig(private val project: Project): BoundSearchableConfigurable(
        displayName = SkateBundle.message("skate.configuration.title"),
        helpTopic = SkateBundle.message("skate.configuration.title"),
        _id = "com.slack.skate.config"
) {
  private val settings = project.service<SkatePluginSettings>()
  override fun createPanel(): DialogPanel {
    return SkateConfigUI(settings, project).createPanel()
  }

}
