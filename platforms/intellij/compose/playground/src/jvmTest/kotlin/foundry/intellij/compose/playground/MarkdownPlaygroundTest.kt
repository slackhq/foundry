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
import com.github.takahirom.roborazzi.RoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.robolectric.annotation.GraphicsMode

const val OUTPUT_DIRECTORY_PATH =
  "src/jvmTest/kotlin/foundry/intellij/compose/playground/screenshots"

@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarkdownPlaygroundTest {
  @OptIn(ExperimentalTestApi::class)
  @Test
  fun test() = runTest {
    runDesktopComposeUiTest {
      setContent { MarkdownPlayground() }

      val roborazziOptions =
        RoborazziOptions(
          recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
          compareOptions =
            RoborazziOptions.CompareOptions(outputDirectoryPath = OUTPUT_DIRECTORY_PATH),
        )

      onRoot().captureRoboImage(roborazziOptions = roborazziOptions)

      onNodeWithTag("dark-mode-toggle").performClick()

      onRoot().captureRoboImage(roborazziOptions = roborazziOptions)
    }
  }
}
