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
package foundry.intellij.skate.ui

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import foundry.intellij.skate.SkatePluginSettings
import foundry.intellij.skate.codeowners.CodeOwnerFileFetcherImpl

class CodeOwnerWidgetFactory : StatusBarWidgetFactory {

  private val logger = logger<CodeOwnerWidget>()

  override fun getId() = CODE_OWNER_WIDGET_ID

  override fun getDisplayName() = "Code Owners"

  override fun isAvailable(project: Project): Boolean {
    val isSettingEnabled = project.getService(SkatePluginSettings::class.java).isCodeOwnerEnabled
    val isEnabled =
      isSettingEnabled && CodeOwnerFileFetcherImpl(project).getCodeOwnershipFile() != null
    logger.debug("CodeOwnerWidgetFactory.isAvailable result: $isEnabled")
    return isEnabled
  }

  override fun createWidget(project: Project) = CodeOwnerWidget(project)

  override fun disposeWidget(widget: StatusBarWidget) = Disposer.dispose(widget)

  override fun canBeEnabledOn(statusBar: StatusBar) = true
}
