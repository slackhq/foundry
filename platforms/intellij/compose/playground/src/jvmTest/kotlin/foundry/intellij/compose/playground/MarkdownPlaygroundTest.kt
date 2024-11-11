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
package foundry.intellij.compose.playground

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.github.takahirom.roborazzi.DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test

class MarkdownPlaygroundTest {
  @OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class)
  @Test
  fun snapshot() = runDesktopComposeUiTest {
    setContent { MarkdownPlayground() }

    val roborazziOptions =
      RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
        compareOptions =
          RoborazziOptions.CompareOptions(outputDirectoryPath = DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH),
      )

    onRoot().captureRoboImage(roborazziOptions = roborazziOptions)

    onNodeWithTag(TestTags.DARK_MODE_TOGGLE).performClick()

    onRoot().captureRoboImage(roborazziOptions = roborazziOptions)
  }
}
