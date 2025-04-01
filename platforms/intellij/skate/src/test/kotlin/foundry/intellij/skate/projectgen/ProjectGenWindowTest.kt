/*
 * Copyright (C) 2025 Slack Technologies, LLC
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
package foundry.intellij.skate.projectgen

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import java.io.File
import org.junit.Test

class ProjectGenWindowTest : LightPlatformCodeInsightFixture4TestCase() {

  override fun setUp() {
    super.setUp()
    ensureProjectBasePathExists()
  }

  // add this function so each test has an isolated temp dir
  private fun ensureProjectBasePathExists() {
    val basePath = project.basePath ?: return
    val dir = File(basePath)

    if (!dir.exists()) {
      dir.mkdirs()
    }
  }

  @Test
  fun `test onOK callback is invoked on OK`() {
    var wasCalled = false
    val dialog = ProjectGenWindow(project, null).apply { onOk = { wasCalled = true } }

    // simulate clicking OK
    dialog.doOKAction()

    assertTrue("expect onOk is called when OK is pressed", wasCalled)
  }

  @Test
  fun `test onOK callback is invoked on dismissDialogAndSync`() {
    var wasCalled = false
    val dialog = ProjectGenWindow(project, null).apply { onOk = { wasCalled = true } }

    dialog.dismissDialogAndSync()

    assertTrue("expect onOk is called when dismissDialogAndSync is invoked", wasCalled)
  }

  @Test
  fun `test onOk is not called on cancel`() {
    var wasCalled = false
    val dialog = ProjectGenWindow(project, null).apply { onOk = { wasCalled = true } }

    dialog.doCancelAction()

    assertTrue("onOk should not be called when cancel is pressed", !wasCalled)
  }

  @Test
  fun `test dialog is not modal`() {
    // dialog must be modeless to avoid deadlocking the EDT when triggering Android.SyncProject.
    // See: https://jb.gg/ij-platform-threading, nonModal() modality state
    val dialog = ProjectGenWindow(project, null)

    assertTrue("dialog should be modeless to avoid EDT deadlock", !dialog.isModal)
  }
}
