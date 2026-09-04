package com.novelstudio.feature.workbench

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.novelstudio.core.model.TagSuggestion

/** 当前光标所在的逗号分隔标签，以及可安全替换的 UTF-16 文本范围。 */
internal data class PromptToken(
    val range: TextRange,
    val query: String,
)

/** 选中联想词后的新文本与光标位置。 */
internal data class PromptReplacement(
    val text: String,
    val cursor: Int,
)

/**
 * 找出光标所在标签。逗号和换行是分隔符，标签外层的 `{` / `[` 权重标记会被保留，
 * 不参与 Trie 查询与替换。
 */
internal fun promptTokenAtCursor(text: String, cursor: Int): PromptToken? {
    if (cursor !in 0..text.length) return null

    var segmentStart = cursor
    while (segmentStart > 0 && !text[segmentStart - 1].isPromptSeparator()) segmentStart--

    var segmentEnd = cursor
    while (segmentEnd < text.length && !text[segmentEnd].isPromptSeparator()) segmentEnd++

    var contentStart = segmentStart
    while (
        contentStart < segmentEnd &&
        (text[contentStart].isWhitespace() || text[contentStart].isWeightOpener())
    ) {
        contentStart++
    }

    var contentEnd = segmentEnd
    while (
        contentEnd > contentStart &&
        (text[contentEnd - 1].isWhitespace() || text[contentEnd - 1].isWeightCloser())
    ) {
        contentEnd--
    }

    val queryEnd = cursor.coerceIn(contentStart, contentEnd)
    if (queryEnd <= contentStart) return null
    val query = text.substring(contentStart, queryEnd).trimEnd()
    if (query.isEmpty()) return null

    return PromptToken(TextRange(contentStart, contentEnd), query)
}

internal fun replacePromptToken(text: String, token: PromptToken, suggestion: String): PromptReplacement {
    val start = token.range.start.coerceIn(0, text.length)
    val end = token.range.end.coerceIn(start, text.length)
    val normalizedSuggestion = suggestion.trim()
    val replaced = text.replaceRange(start, end, normalizedSuggestion)
    return PromptReplacement(replaced, start + normalizedSuggestion.length)
}

private fun Char.isPromptSeparator(): Boolean = this == ',' || this == '\n' || this == '\r'

private fun Char.isWeightOpener(): Boolean = this == '{' || this == '['

private fun Char.isWeightCloser(): Boolean = this == '}' || this == ']'

/** 中文等组合输入尚未提交时，Enter 等按键必须继续交给输入法。 */
internal fun canHandlePromptSuggestionShortcut(value: TextFieldValue): Boolean = value.composition == null

/**
 * Prompt 编辑器：保留 IME composition 与光标，展示 Trie 联想，并对 NovelAI 权重语法做等长高亮。
 */
@Composable
internal fun PromptEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    suggestions: List<TagSuggestion>,
    onSuggestionAccepted: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedIndex by remember(suggestions) { mutableIntStateOf(0) }
    var dismissedAt by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val token = promptTokenAtCursor(value.text, value.selection.end)
    val editingPoint = value.text to value.selection.end
    val hasSuggestions = token != null && suggestions.isNotEmpty() && dismissedAt != editingPoint

    fun acceptSuggestion(index: Int): Boolean {
        val activeToken = token ?: return false
        val suggestion = suggestions.getOrNull(index) ?: return false
        val replacement = replacePromptToken(value.text, activeToken, suggestion.tag)
        dismissedAt = replacement.text to replacement.cursor
        onValueChange(
            TextFieldValue(
                text = replacement.text,
                selection = TextRange(replacement.cursor),
            ),
        )
        onSuggestionAccepted(suggestion.tag)
        return true
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { nextValue ->
                if (nextValue.text to nextValue.selection.end != dismissedAt) dismissedAt = null
                onValueChange(nextValue)
            },
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (
                        !hasSuggestions ||
                        !canHandlePromptSuggestionShortcut(value) ||
                        event.type != KeyEventType.KeyDown
                    ) {
                        return@onPreviewKeyEvent false
                    }
                    when (event.key) {
                        Key.DirectionDown -> {
                            selectedIndex = (selectedIndex + 1).coerceAtMost(suggestions.lastIndex)
                            true
                        }
                        Key.DirectionUp -> {
                            selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                            true
                        }
                        Key.Enter, Key.Tab -> acceptSuggestion(selectedIndex)
                        Key.Escape -> {
                            dismissedAt = editingPoint
                            true
                        }
                        else -> false
                    }
                },
            label = { Text("正向提示词") },
            placeholder = { Text("masterpiece, best quality, 1girl, solo ...") },
            minLines = 3,
            shape = MaterialTheme.shapes.medium,
            visualTransformation = PromptWeightVisualTransformation(
                boostedColor = MaterialTheme.colorScheme.tertiary,
                weakenedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
            ),
        )

        AnimatedVisibility(visible = hasSuggestions) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    suggestions.forEachIndexed { index, suggestion ->
                        TextButton(
                            onClick = { acceptSuggestion(index) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = suggestion.tag,
                                    fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                Text(
                                    text = "${suggestion.count} · ${(suggestion.confidence * 100).toInt()}%",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 高亮 `{增强}` 与 `[弱化]`，不改变任何字符，因此光标映射恒为 Identity。 */
internal class PromptWeightVisualTransformation(
    private val boostedColor: Color,
    private val weakenedColor: Color,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val styled = AnnotatedString.Builder(text)
        var curlyDepth = 0
        var squareDepth = 0

        text.forEachIndexed { index, char ->
            when (char) {
                '{' -> curlyDepth++
                '[' -> squareDepth++
            }

            when {
                curlyDepth > 0 -> styled.addStyle(
                    SpanStyle(color = boostedColor, fontWeight = FontWeight.SemiBold),
                    index,
                    index + 1,
                )
                squareDepth > 0 -> styled.addStyle(
                    SpanStyle(color = weakenedColor),
                    index,
                    index + 1,
                )
            }

            when (char) {
                '}' -> curlyDepth = (curlyDepth - 1).coerceAtLeast(0)
                ']' -> squareDepth = (squareDepth - 1).coerceAtLeast(0)
            }
        }

        return TransformedText(styled.toAnnotatedString(), OffsetMapping.Identity)
    }
}
