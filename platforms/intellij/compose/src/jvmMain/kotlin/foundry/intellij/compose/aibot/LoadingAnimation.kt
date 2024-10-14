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
package foundry.intellij.compose.aibot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * This shows a loading animation of three bouncing dots that occurs when the user is waiting on a
 * response.
 *
 * Adopted from the Three-Dot Loading Animation Tutorial with Jetpack Compose by Stevdza-San from
 *
 * @see <a href="https://www.youtube.com/watch?v=xakNOVaYLAg">Source</a>
 */
@Composable
fun LoadingAnimation(
  modifier: Modifier = Modifier,
  dotSize: Dp = 7.dp,
  dotColor: Color = JewelTheme.contentColor,
  spacing: Dp = 7.dp,
  movementDistance: Dp = 10.dp,
) {

  val animatedDots = remember {
    mutableStateListOf<Animatable<Float, AnimationVector1D>>().apply {
      repeat(4) { add(Animatable(0f)) }
    }
  }
  animatedDots.forEachIndexed { index, dot ->
    LaunchedEffect(dot) {
      delay(index * 100L)
      dot.animateTo(
        targetValue = 1f,
        animationSpec =
          infiniteRepeatable(
            animation =
              keyframes {
                durationMillis = 1200
                0.0f at 0 using LinearOutSlowInEasing
                1.0f at 300 using LinearOutSlowInEasing
                0.0f at 600 using LinearOutSlowInEasing
                0.0f at 1200 using LinearOutSlowInEasing
              },
            repeatMode = RepeatMode.Restart,
          ),
      )
    }
  }

  val animatedValues = animatedDots.map { it.value }
  val pixelDistance = with(LocalDensity.current) { movementDistance.toPx() }

  Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(spacing)) {
    animatedValues.forEach { value ->
      Box(
        modifier =
          Modifier.size(dotSize)
            .graphicsLayer { translationY = -value * pixelDistance }
            .background(color = dotColor, shape = CircleShape)
      )
    }
  }
}
