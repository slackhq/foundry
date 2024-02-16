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
