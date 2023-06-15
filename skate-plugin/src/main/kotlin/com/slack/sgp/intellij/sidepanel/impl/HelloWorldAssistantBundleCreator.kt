package com.slack.sgp.intellij.sidepanel.impl

import com.intellij.openapi.project.Project
import com.slack.sgp.intellij.sidepanel.AssistantBundleCreator
import com.slack.sgp.intellij.sidepanel.TutorialBundleData
import java.net.URL
import org.jetbrains.annotations.NotNull

class HelloWorldAssistantBundleCreator : AssistantBundleCreator {

  override fun getBundleId(): String {
    return "HelloWorldBundle"
  }

  override fun getBundle(@NotNull project: Project): TutorialBundleData? {
    return HelloWorldTutorialBundleData()
  }

  override fun getConfig(): URL? {
    return null
  }
}
