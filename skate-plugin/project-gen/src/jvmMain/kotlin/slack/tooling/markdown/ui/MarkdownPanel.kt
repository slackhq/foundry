package slack.tooling.markdown.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.model.DefaultMarkdownColors
import com.mikepenz.markdown.model.DefaultMarkdownTypography
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownTypography
import java.awt.Dimension
import javax.swing.JComponent
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Typography
import org.jetbrains.jewel.ui.component.Typography.labelTextSize
import org.jetbrains.jewel.ui.component.minus
import org.jetbrains.jewel.ui.theme.colorPalette
import slack.tooling.projectgen.SlackDesktopTheme

object MarkdownPanel {
  fun createPanel(computeMarkdown: suspend () -> String): JComponent {
    return ComposePanel().apply {
      // Necessary to avoid an NPE in JPanel
      // This is just a minimum
      preferredSize = Dimension(400, 600)
      setContent { SlackDesktopTheme { MarkdownContent(computeMarkdown) } }
    }
  }
}

@Composable
fun MarkdownContent(computeMarkdown: suspend () -> String) {
  CompositionLocalProvider(
    LocalMarkdownColors provides jewelMarkdownColor(),
    LocalMarkdownTypography provides jewelMarkdownTypography(),
  ) {
    val markdown by produceState<String?>(null) { value = computeMarkdown() }
    if (markdown == null) {
      Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        CircularProgressIndicator()
        Text("Loading…", style = Typography.h0TextStyle())
      }
    } else {
      val stateVertical = rememberScrollState(0)
      Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().verticalScroll(stateVertical).padding(16.dp)) {
          Markdown(
            markdown!!,
            colors = LocalMarkdownColors.current,
            typography = LocalMarkdownTypography.current,
          )
        }

        VerticalScrollbar(
          modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
          adapter = rememberScrollbarAdapter(stateVertical),
        )
      }
    }
  }
}

@Composable
private fun jewelMarkdownColor(
  text: Color = JewelTheme.defaultTextStyle.color.takeOrElse { JewelTheme.contentColor },
  linkText: Color = JewelTheme.linkColor,
  // TODO https://github.com/mikepenz/multiplatform-markdown-renderer/issues/131
  dividerColor: Color = JewelTheme.globalColors.outlines.focused,
): MarkdownColors {
  // TODO https://github.com/mikepenz/multiplatform-markdown-renderer/issues/130
  val (codeText, codeBackground, _, inlineCodeBackground) =
    rememberCodeBackground(JewelTheme.globalColors.paneBackground, text)
  return DefaultMarkdownColors(
    text = text,
    codeText = codeText,
    linkText = linkText,
    codeBackground = codeBackground,
    inlineCodeBackground = inlineCodeBackground,
    dividerColor = dividerColor,
  )
}

private val JewelTheme.Companion.linkColor: Color
  @Composable
  get() {
    return if (isDark) {
      colorPalette.blue.last()
    } else {
      colorPalette.blue.first()
    }
  }

@Immutable
private data class CodeColors(
  val codeText: Color,
  val codeBackground: Color,
  val inlineCodeText: Color,
  val inlineCodeBackground: Color,
)

@Composable
private fun rememberCodeBackground(
  panelBackground: Color,
  textColor: Color,
  isDark: Boolean = JewelTheme.isDark,
): CodeColors {
  // If we're in a light theme, use a darker color. If we're in a dark them, lighten it slightly
  return remember(panelBackground, isDark) {
    if (isDark) {
      CodeColors(
        codeText = textColor,
        codeBackground = panelBackground.darken(0.2f),
        inlineCodeText = textColor.darken(0.2f),
        inlineCodeBackground = panelBackground.lighten(0.1f),
      )
    } else {
      CodeColors(
        codeText = textColor,
        codeBackground = panelBackground.darken(0.05f),
        inlineCodeText = textColor,
        inlineCodeBackground = panelBackground.darken(0.05f),
      )
    }
  }
}

private fun Color.darken(factor: Float): Color {
  return Color(
    red = red * (1 - factor),
    green = green * (1 - factor),
    blue = blue * (1 - factor),
    alpha = 1f,
  )
}

private fun Color.lighten(factor: Float): Color {
  return Color(
    red = red + (1 - red) * factor,
    green = green + (1 - green) * factor,
    blue = blue + (1 - blue) * factor,
    alpha = 1f,
  )
}

@Composable
private fun jewelMarkdownTypography(
  text: TextStyle = JewelTheme.defaultTextStyle,
  code: TextStyle =
    JewelTheme.defaultTextStyle.copy(
      fontSize = JewelTheme.defaultTextStyle.fontSize - 1.sp,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Medium,
      lineHeight = labelTextSize() * 1.5,
    ),
  h1: TextStyle =
    JewelTheme.defaultTextStyle.copy(fontSize = labelTextSize() * 2, fontWeight = FontWeight.Bold),
  h2: TextStyle =
    JewelTheme.defaultTextStyle.copy(
      fontSize = labelTextSize() * 1.8,
      fontWeight = FontWeight.Bold,
    ),
  h3: TextStyle =
    JewelTheme.defaultTextStyle.copy(
      fontSize = labelTextSize() * 1.6,
      fontWeight = FontWeight.Bold,
    ),
  h4: TextStyle =
    JewelTheme.defaultTextStyle.copy(
      fontSize = labelTextSize() * 1.2,
      fontWeight = FontWeight.Bold,
    ),
  h5: TextStyle = JewelTheme.defaultTextStyle.copy(fontWeight = FontWeight.Bold),
  h6: TextStyle =
    JewelTheme.defaultTextStyle.copy(
      fontSize = labelTextSize() * 0.85,
      fontWeight = FontWeight.Bold,
      color =
        if (JewelTheme.isDark) {
          JewelTheme.defaultTextStyle.color.takeOrElse { Color.Gray }
        } else {
          JewelTheme.defaultTextStyle.color.lighten(0.3f)
        },
    ),
  quote: TextStyle =
    JewelTheme.defaultTextStyle
      .copy(
        color =
          if (JewelTheme.isDark) {
            JewelTheme.defaultTextStyle.color.takeOrElse { Color.Gray }
          } else {
            JewelTheme.defaultTextStyle.color.lighten(0.3f)
          }
      )
      .plus(SpanStyle(fontStyle = FontStyle.Italic)),
  paragraph: TextStyle = text,
  ordered: TextStyle = text,
  bullet: TextStyle = text,
  list: TextStyle = text,
): MarkdownTypography =
  DefaultMarkdownTypography(
    h1 = h1,
    h2 = h2,
    h3 = h3,
    h4 = h4,
    h5 = h5,
    h6 = h6,
    text = text,
    quote = quote,
    code = code,
    paragraph = paragraph,
    ordered = ordered,
    bullet = bullet,
    list = list,
  )
