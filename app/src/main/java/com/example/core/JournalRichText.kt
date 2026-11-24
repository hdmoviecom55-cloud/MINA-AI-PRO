package com.example.core

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

object JournalRichText {

    const val MAX_JOURNAL_LENGTH = 5000

    fun applyBold(value: TextFieldValue): TextFieldValue =
        applyWrapFormat(value, marker = "**")

    fun applyItalic(value: TextFieldValue): TextFieldValue =
        applyWrapFormat(value, marker = "*")

    fun toggleBulletList(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val rawStart = minOf(value.selection.start, value.selection.end).coerceIn(0, text.length)
        val rawEnd = maxOf(value.selection.start, value.selection.end).coerceIn(0, text.length)

        val lineStart = text.lastIndexOf('\n', (rawStart - 1).coerceAtLeast(0)).let { idx ->
            if (rawStart == 0 || idx < 0) 0 else idx + 1
        }
        val lineEnd = text.indexOf('\n', rawEnd).let { idx ->
            if (idx < 0) text.length else idx
        }

        val block = text.substring(lineStart, lineEnd)
        val lines = block.split('\n')

        val nonEmptyLines = lines.filter { it.isNotBlank() }
        val shouldRemove = if (lines.size == 1) {
            isBulletLine(lines.first())
        } else {
            nonEmptyLines.isNotEmpty() && nonEmptyLines.all { isBulletLine(it) }
        }

        val updatedLines = lines.map { line ->
            if (shouldRemove) {
                stripBulletPrefix(line)
            } else if (isBulletLine(line)) {
                line
            } else {
                "- $line"
            }
        }

        val replacement = updatedLines.joinToString("\n")
        val updatedText = text.substring(0, lineStart) + replacement + text.substring(lineEnd)
        if (updatedText.length > MAX_JOURNAL_LENGTH) return value

        val newCursor = if (rawStart == rawEnd) {
            val delta = replacement.length - block.length
            (rawEnd + delta).coerceIn(lineStart, lineStart + replacement.length)
        } else {
            lineStart + replacement.length
        }

        return value.copy(
            text = updatedText,
            selection = if (rawStart == rawEnd) {
                TextRange(newCursor)
            } else {
                TextRange(lineStart, (lineStart + replacement.length).coerceAtMost(updatedText.length))
            }
        )
    }

    fun stripFormattingForSearch(raw: String): String {
        if (raw.isEmpty()) return ""
        return raw.lines().joinToString("\n") { line ->
            val withoutBullet = stripBulletPrefix(line)
            withoutBullet
                .replace("***", "")
                .replace("**", "")
                .replace("*", "")
        }
    }

    fun isBulletLine(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.startsWith("- ") || trimmed.startsWith("• ")
    }

    fun stripBulletPrefix(line: String): String {
        val leadingSpaces = line.takeWhile { it == ' ' }
        val rest = line.drop(leadingSpaces.length)
        return when {
            rest.startsWith("- ") -> leadingSpaces + rest.removePrefix("- ")
            rest.startsWith("• ") -> leadingSpaces + rest.removePrefix("• ")
            else -> line
        }
    }

    fun parseInlineRichText(input: String): AnnotatedString {
        return buildAnnotatedString {
            var i = 0
            val len = input.length
            while (i < len) {
                when {
                    input.startsWith("***", i) -> {
                        val close = input.indexOf("***", i + 3)
                        if (close > i + 3) {
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontStyle = FontStyle.Italic
                                )
                            ) {
                                append(input.substring(i + 3, close))
                            }
                            i = close + 3
                        } else {
                            append("***")
                            i += 3
                        }
                    }

                    input.startsWith("**", i) -> {
                        val close = input.indexOf("**", i + 2)
                        if (close > i + 2) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(input.substring(i + 2, close))
                            }
                            i = close + 2
                        } else {
                            append("**")
                            i += 2
                        }
                    }

                    input[i] == '*' -> {
                        val close = findClosingSingleAsterisk(input, i + 1)
                        if (close > i + 1) {
                            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                append(input.substring(i + 1, close))
                            }
                            i = close + 1
                        } else {
                            append('*')
                            i += 1
                        }
                    }

                    else -> {
                        val nextMarker = input.indexOf('*', i)
                        if (nextMarker == -1) {
                            append(input.substring(i))
                            i = len
                        } else if (nextMarker > i) {
                            append(input.substring(i, nextMarker))
                            i = nextMarker
                        } else {
                            append(input[i])
                            i++
                        }
                    }
                }
            }
        }
    }

    private fun findClosingSingleAsterisk(input: String, startIndex: Int): Int {
        var idx = startIndex
        while (idx < input.length) {
            if (input[idx] == '*') {
                val prevIsStar = idx > 0 && input[idx - 1] == '*'
                val nextIsStar = idx + 1 < input.length && input[idx + 1] == '*'
                if (!prevIsStar && !nextIsStar) {
                    return idx
                }
            }
            idx++
        }
        return -1
    }

    private fun applyWrapFormat(value: TextFieldValue, marker: String): TextFieldValue {
        val text = value.text
        val start = minOf(value.selection.start, value.selection.end).coerceIn(0, text.length)
        val end = maxOf(value.selection.start, value.selection.end).coerceIn(0, text.length)
        val mLen = marker.length

        if (start == end) {
            if (text.length + mLen * 2 > MAX_JOURNAL_LENGTH) return value
            val newText = text.substring(0, start) + marker + marker + text.substring(end)
            return value.copy(
                text = newText,
                selection = TextRange(start + mLen)
            )
        }

        val selected = text.substring(start, end)
        val selectedWrapped = selected.length > mLen * 2 &&
            selected.startsWith(marker) &&
            selected.endsWith(marker) &&
            !(marker == "*" && selected.startsWith("**") && selected.endsWith("**"))

        if (selectedWrapped) {
            val unwrapped = selected.substring(mLen, selected.length - mLen)
            val newText = text.substring(0, start) + unwrapped + text.substring(end)
            return value.copy(
                text = newText,
                selection = TextRange(start, start + unwrapped.length)
            )
        }

        val hasOuterPrefix = start >= mLen && text.substring(start - mLen, start) == marker
        val hasOuterSuffix = end + mLen <= text.length && text.substring(end, end + mLen) == marker
        val singleStarConflict = marker == "*" && (
            (start >= 2 && text.substring(start - 2, start) == "**") ||
                (end + 2 <= text.length && text.substring(end, end + 2) == "**")
            )

        if (hasOuterPrefix && hasOuterSuffix && !singleStarConflict) {
            val newText = text.substring(0, start - mLen) + selected + text.substring(end + mLen)
            return value.copy(
                text = newText,
                selection = TextRange(start - mLen, end - mLen)
            )
        }

        if (text.length + mLen * 2 > MAX_JOURNAL_LENGTH) return value
        val newText = text.substring(0, start) + marker + selected + marker + text.substring(end)
        return value.copy(
            text = newText,
            selection = TextRange(start + mLen, end + mLen)
        )
    }
}

@Composable
fun RichJournalText(
    text: String,
    bulletColor: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLinesCollapsed: Int = Int.MAX_VALUE
) {
    val lines = remember(text) { text.split('\n') }
    val displayLines = remember(lines, maxLinesCollapsed) {
        if (maxLinesCollapsed < Int.MAX_VALUE && lines.size > maxLinesCollapsed) {
            lines.take(maxLinesCollapsed)
        } else {
            lines
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        displayLines.forEachIndexed { index, rawLine ->
            val isLastTruncated = maxLinesCollapsed < Int.MAX_VALUE &&
                index == displayLines.lastIndex &&
                lines.size > maxLinesCollapsed

            if (JournalRichText.isBulletLine(rawLine)) {
                val content = JournalRichText.stripBulletPrefix(rawLine)
                val annotated = remember(content) { JournalRichText.parseInlineRichText(content) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "•",
                        style = style.copy(fontWeight = FontWeight.Bold),
                        color = bulletColor
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isLastTruncated) AnnotatedString("${annotated.text}…") else annotated,
                        style = style,
                        maxLines = if (maxLinesCollapsed < Int.MAX_VALUE) 3 else Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else if (rawLine.isBlank()) {
                Spacer(Modifier.width(1.dp))
            } else {
                val annotated = remember(rawLine) { JournalRichText.parseInlineRichText(rawLine) }
                Text(
                    text = annotated,
                    style = style,
                    maxLines = if (maxLinesCollapsed < Int.MAX_VALUE) 4 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
