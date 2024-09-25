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
package slack.tooling.aibot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.decodeToImageVector
import org.jetbrains.compose.resources.decodeToSvgPainter

// Migration snippet copied from https://github.com/JetBrains/compose-multiplatform-core/pull/1457
// To resolve deprecated painterResource function from upgrading compose-jb
@Composable
fun painterResource(resourcePath: String): Painter =
  when (resourcePath.substringAfterLast(".")) {
    "svg" -> rememberSvgResource(resourcePath)
    "xml" -> rememberVectorXmlResource(resourcePath)
    else -> rememberBitmapResource(resourcePath)
  }

@OptIn(ExperimentalResourceApi::class)
@Composable
internal fun rememberBitmapResource(path: String): Painter {
  return remember(path) { BitmapPainter(readResourceBytes(path).decodeToImageBitmap()) }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
internal fun rememberVectorXmlResource(path: String): Painter {
  val density = LocalDensity.current
  val imageVector = remember(density, path) { readResourceBytes(path).decodeToImageVector(density) }
  return rememberVectorPainter(imageVector)
}

@OptIn(ExperimentalResourceApi::class)
@Composable
internal fun rememberSvgResource(path: String): Painter {
  val density = LocalDensity.current
  return remember(density, path) { readResourceBytes(path).decodeToSvgPainter(density) }
}

private object ResourceLoader

private fun readResourceBytes(resourcePath: String) =
  ResourceLoader.javaClass.classLoader.getResourceAsStream(resourcePath).readAllBytes()
