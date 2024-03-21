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
package com.slack.sgp.intellij.projectgen

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class PrefixTransformation(val prefix: String) : VisualTransformation {
  override fun filter(text: AnnotatedString): TransformedText {
    return prefixFilter(text, prefix)
  }
}

private fun prefixFilter(number: AnnotatedString, prefix: String): TransformedText {

  val out = prefix + number.text
  val prefixOffset = prefix.length

  val numberOffsetTranslator =
    object : OffsetMapping {
      override fun originalToTransformed(offset: Int): Int {
        return offset + prefixOffset
      }

      override fun transformedToOriginal(offset: Int): Int {
        if (offset <= prefixOffset - 1) return prefixOffset
        return offset - prefixOffset
      }
    }

  return TransformedText(AnnotatedString(out), numberOffsetTranslator)
}
