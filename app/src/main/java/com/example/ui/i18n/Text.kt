package com.example.ui.i18n

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * نفس Text حق Material3 بس يترجم نص الواجهة لما اللغة إنجليزي.
 * لمحتوى التجار والعملاء (أسماء، عناوين عروض، ملاحظات) نستخدم androidx.compose.material3.Text مباشرة.
 */
@Composable
fun Text(
    text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null, fontWeight: FontWeight? = null, fontFamily: FontFamily? = null, letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null, textAlign: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true, maxLines: Int = Int.MAX_VALUE, minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null, style: TextStyle = LocalTextStyle.current
) = androidx.compose.material3.Text(
    text = I18n.tr(text), modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
    letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight, overflow = overflow,
    softWrap = softWrap, maxLines = maxLines, minLines = minLines, onTextLayout = onTextLayout, style = style
)

@Composable
fun Text(
    text: AnnotatedString, modifier: Modifier = Modifier, color: Color = Color.Unspecified, fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null, fontWeight: FontWeight? = null, fontFamily: FontFamily? = null, letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null, textAlign: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true, maxLines: Int = Int.MAX_VALUE, minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {}, style: TextStyle = LocalTextStyle.current
) = androidx.compose.material3.Text(
    text = text, modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
    letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight, overflow = overflow,
    softWrap = softWrap, maxLines = maxLines, minLines = minLines, onTextLayout = onTextLayout, style = style
)

/**
 * نفس Surface حق Material3، بس في المظهر الداكن: لو لون الخلفية فاتح (لون ثابت من التصميم) نخلي الكلام داكن عشان يقرا.
 */
@Composable
fun Surface(
    modifier: Modifier = Modifier, shape: Shape = RectangleShape, color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = autoContent(color), tonalElevation: Dp = 0.dp, shadowElevation: Dp = 0.dp, border: BorderStroke? = null,
    content: @Composable () -> Unit
) = androidx.compose.material3.Surface(modifier = modifier, shape = shape, color = color, contentColor = contentColor,
    tonalElevation = tonalElevation, shadowElevation = shadowElevation, border = border, content = content)

@Composable
private fun autoContent(c: Color): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.3f
    return if (dark && c.isSpecified() && c.alpha > 0.5f && c.luminance() > 0.55f) Color(0xFF1F2937) else contentColorFor(c)
}
private fun Color.isSpecified() = this != Color.Unspecified
