package com.slack.sgp.intellij.sidepanel.impl

import com.slack.sgp.intellij.sidepanel.TutorialBundleData
import javax.swing.Icon

class HelloWorldTutorialBundleData : TutorialBundleData {

  private var resourceClass: Class<*>? = null
  private var bundleCreatorId: String = "HelloWorldBundle"

  override fun setResourceClass(clazz: Class<*>) {
    this.resourceClass = clazz
  }

  override fun getName(): String {
    return "Hello World"
  }

  override fun getIcon(): Icon? {
    return null
  }

  override fun getLogo(): Icon? {
    return null
  }

  override fun getWelcome(): String {
    return "Hello!!! Welcome!!"
  }

  override fun getBundleCreatorId(): String {
    return bundleCreatorId
  }

  override fun setBundleCreatorId(bundleCreatorId: String) {
    this.bundleCreatorId = bundleCreatorId
  }

  override fun isStepByStep(): Boolean {
    return false
  }

  override fun hideStepIndex(): Boolean {
    return false
  }
}
