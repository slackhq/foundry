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
package com.slack.sgp.intellij.util

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.slack.sgp.intellij.SkatePluginSettings

fun Project.settings(): SkatePluginSettings = service<SkatePluginSettings>()

fun Project.isLinkifiedFeatureFlagsEnabled(): Boolean = settings().isLinkifiedFeatureFlagsEnabled

fun Project.featureFlagFilePattern(): String? = settings().featureFlagFilePattern

fun Project.isTracingEnabled(): Boolean = settings().isTracingEnabled

fun Project.isProjectGenMenuActionEnabled(): Boolean = settings().isProjectGenMenuActionEnabled

fun Project.tracingEndpoint(): String? = settings().tracingEndpoint
