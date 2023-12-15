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

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import org.jetbrains.annotations.VisibleForTesting

@VisibleForTesting
internal const val DEFAULT_TRANSLATOR_SOURCE_MODELS_PACKAGE_NAME = "slack.api.schemas"
@VisibleForTesting internal const val DEFAULT_TRANSLATOR_FILE_NAME_SUFFIX = "Translator.kt"
@VisibleForTesting internal const val DEFAULT_TRANSLATOR_ENUM_IDENTIFIER = "name"
internal const val DEFAULT_PROJECT_GEN_CLI_COMMAND = "./slackw generate-project"
internal const val DEFAULT_CODE_OWNER_FILE_PATH = "config/code-ownership/code_ownership.csv"

/** Manages user-specific settings for the Skate plugin */
@Service(Service.Level.PROJECT)
@State(name = "SkatePluginSettings", storages = [Storage("skate.xml")])
class SkatePluginSettings : SimplePersistentStateComponent<SkatePluginSettings.State>(State()) {

  var whatsNewFilePath: String
    get() = state.whatsNewFilePath ?: "WHATSNEW.md"
    set(value) {
      state.whatsNewFilePath = value
    }

  var isWhatsNewEnabled: Boolean
    get() = state.isWhatsNewEnabled
    set(value) {
      state.isWhatsNewEnabled = value
    }

  var translatorSourceModelsPackageName: String
    get() = state.translatorSourceModelsPackageName ?: DEFAULT_TRANSLATOR_SOURCE_MODELS_PACKAGE_NAME
    set(value) {
      state.translatorSourceModelsPackageName = value
    }

  var translatorFileNameSuffix: String
    get() = state.translatorFileNameSuffix ?: DEFAULT_TRANSLATOR_FILE_NAME_SUFFIX
    set(value) {
      state.translatorFileNameSuffix = value
    }

  var translatorEnumIdentifier: String
    get() = state.translatorEnumIdentifier ?: DEFAULT_TRANSLATOR_ENUM_IDENTIFIER
    set(value) {
      state.translatorEnumIdentifier = value
    }

  var isProjectGenMenuActionEnabled: Boolean
    get() = state.isProjectGenMenuActionEnabled
    set(value) {
      state.isProjectGenMenuActionEnabled = value
    }

  var projectGenRunCommand: String
    get() = state.projectGenCliCommand ?: DEFAULT_PROJECT_GEN_CLI_COMMAND
    set(value) {
      state.projectGenCliCommand = value
    }

  var isLinkifiedFeatureFlagsEnabled: Boolean
    get() = state.isLinkifiedFeatureFlagsEnabled
    set(value) {
      state.isLinkifiedFeatureFlagsEnabled = value
    }

  var featureFlagBaseUrl: String?
    get() = state.featureFlagBaseUrl
    set(value) {
      state.featureFlagBaseUrl = value
    }

  var featureFlagAnnotation: String?
    get() = state.featureFlagAnnotation
    set(value) {
      state.featureFlagAnnotation = value
    }

  /**
   * Regex pattern to match file name for feature flag checks. E.g.,".*Feature(s)?\\.kt$"
   *
   * This property is used to determine which Kotlin files in the project should be processed for
   * feature flag annotations based on their file name patterns.
   */
  var featureFlagFilePattern: String?
    get() = state.featureFlagFilePattern
    set(value) {
      state.featureFlagFilePattern = value
    }

  var isTracingEnabled: Boolean
    get() = state.isTracingEnabled
    set(value) {
      state.isTracingEnabled = value
    }

  var tracingEndpoint: String?
    get() = state.tracingEndpoint
    set(value) {
      state.tracingEndpoint = value
    }

  var isCodeOwnerEnabled: Boolean
    get() = state.isCodeOwnerEnabled
    set(value) {
      state.isCodeOwnerEnabled = value
    }

  var codeOwnerFilePath: String?
    get() = state.codeOwnerFilePath ?: DEFAULT_CODE_OWNER_FILE_PATH
    set(value) {
      state.codeOwnerFilePath = value
    }

  class State : BaseState() {
    var whatsNewFilePath by string()
    var isWhatsNewEnabled by property(true)
    var translatorSourceModelsPackageName by string()
    var translatorFileNameSuffix by string()
    var translatorEnumIdentifier by string()
    var isProjectGenMenuActionEnabled by property(true)
    var projectGenCliCommand by string()
    var isLinkifiedFeatureFlagsEnabled by property(false)
    var featureFlagBaseUrl by string()
    var featureFlagAnnotation by string()
    var featureFlagFilePattern by string()
    var isTracingEnabled by property(true)
    var tracingEndpoint by string()
    var codeOwnerFilePath by string()
    var isCodeOwnerEnabled by property(true)
  }
}
