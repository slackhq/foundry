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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.graphics.Color
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
import org.jetbrains.jewel.ui.component.Typography
import org.jetbrains.jewel.ui.component.Typography.labelTextSize
import org.jetbrains.jewel.ui.component.minus
import slack.tooling.projectgen.SlackDesktopTheme

object MarkdownPanel {
  fun createPanel(computeMarkdown: suspend () -> String): JComponent {
    return ComposePanel().apply {
      // Necessary to avoid an NPE in JPanel
      // This is just a minimum
      preferredSize = Dimension(400, 600)
      setContent {
        SlackDesktopTheme {
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
      }
    }
  }
}

@Composable
private fun jewelMarkdownColor(
  text: Color = JewelTheme.defaultTextStyle.color,
  codeText: Color = JewelTheme.defaultTextStyle.color,
  linkText: Color = text,
  codeBackground: Color = rememberCodeBackground(JewelTheme.globalColors.paneBackground),
  inlineCodeBackground: Color = codeBackground,
  dividerColor: Color = JewelTheme.globalColors.borders.normal,
): MarkdownColors =
  DefaultMarkdownColors(
    text = text,
    codeText = codeText,
    linkText = linkText,
    codeBackground = codeBackground,
    inlineCodeBackground = inlineCodeBackground,
    dividerColor = dividerColor,
  )

@Composable
private fun rememberCodeBackground(color: Color, percentage: Float = 30f): Color {
  val newColor =
    remember(color, percentage) {
      val factor = 1 - percentage / 100
      Color(
        red = (color.red * factor).coerceIn(0f, 1f),
        green = (color.green * factor).coerceIn(0f, 1f),
        blue = (color.blue * factor).coerceIn(0f, 1f),
        alpha = color.alpha,
      )
    }
  return newColor
}

@Composable
private fun jewelMarkdownTypography(
  text: TextStyle = JewelTheme.defaultTextStyle,
  code: TextStyle =
    JewelTheme.defaultTextStyle.copy(
      fontSize = labelTextSize() - 2.sp,
      fontFamily = FontFamily.Monospace,
    ),
  h1: TextStyle =
    JewelTheme.defaultTextStyle.copy(fontSize = labelTextSize() * 2, fontWeight = FontWeight.Bold),
  h2: TextStyle =
    JewelTheme.defaultTextStyle.copy(
      fontSize = labelTextSize() * 1.5,
      fontWeight = FontWeight.Bold,
    ),
  h3: TextStyle =
    JewelTheme.defaultTextStyle.copy(
      fontSize = labelTextSize() * 1.25,
      fontWeight = FontWeight.Medium,
    ),
  h4: TextStyle =
    JewelTheme.defaultTextStyle.copy(
      fontSize = labelTextSize() * 1.1,
      fontWeight = FontWeight.Normal,
    ),
  h5: TextStyle =
    JewelTheme.defaultTextStyle.copy(
      fontSize = labelTextSize() * 1.05,
      fontWeight = FontWeight.Normal,
    ),
  h6: TextStyle =
    JewelTheme.defaultTextStyle.copy(
      fontSize = labelTextSize() * 0.95,
      fontWeight = FontWeight.Normal,
    ),
  quote: TextStyle = JewelTheme.defaultTextStyle.plus(SpanStyle(fontStyle = FontStyle.Italic)),
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
