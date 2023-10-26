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

import com.intellij.openapi.wm.*
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase

class WhatsNewToolWindowListenerTest : BasePlatformTestCase() {

  fun testVisibilityChangedFromInitialState() {
    val whatsNewToolWindowListener = WhatsNewToolWindowListener(project)
    val visibilityChanged = whatsNewToolWindowListener.visibilityChanged(true)
    TestCase.assertEquals(visibilityChanged, true)
  }

  fun testVisibilityChangedFalseWhenStateRemains() {
    val whatsNewToolWindowListener = WhatsNewToolWindowListener(project)
    val visibilityChanged = whatsNewToolWindowListener.visibilityChanged(false)
    TestCase.assertEquals(visibilityChanged, false)
  }
}
