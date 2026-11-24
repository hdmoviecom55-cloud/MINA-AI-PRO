package com.example

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import com.example.core.JournalRichText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalRichTextTest {

    @Test
    fun applyBold_wrapsAndUnwrapsSelectedText() {
        val initial = TextFieldValue("Today was great", selection = TextRange(10, 15))
        val bolded = JournalRichText.applyBold(initial)
        assertEquals("Today was **great**", bolded.text)
        assertEquals(TextRange(12, 17), bolded.selection)

        val unbolded = JournalRichText.applyBold(bolded)
        assertEquals("Today was great", unbolded.text)
        assertEquals(TextRange(10, 15), unbolded.selection)
    }

    @Test
    fun applyBold_collapsedCursorInsertsMarkersWithCursorInside() {
        val initial = TextFieldValue("Hello ", selection = TextRange(6))
        val updated = JournalRichText.applyBold(initial)
        assertEquals("Hello ****", updated.text)
        assertEquals(TextRange(8), updated.selection)
    }

    @Test
    fun applyItalic_wrapsAndUnwrapsWithoutConflictingWithBold() {
        val initial = TextFieldValue("Feeling calm", selection = TextRange(8, 12))
        val italicized = JournalRichText.applyItalic(initial)
        assertEquals("Feeling *calm*", italicized.text)
        assertEquals(TextRange(9, 13), italicized.selection)

        val reverted = JournalRichText.applyItalic(italicized)
        assertEquals("Feeling calm", reverted.text)
        assertEquals(TextRange(8, 12), reverted.selection)
    }

    @Test
    fun toggleBulletList_addsAndRemovesBulletsForMultiLineSelection() {
        val initial = TextFieldValue("First win\nSecond win", selection = TextRange(0, 20))
        val bulleted = JournalRichText.toggleBulletList(initial)
        assertEquals("- First win\n- Second win", bulleted.text)

        val unbulleted = JournalRichText.toggleBulletList(bulleted)
        assertEquals("First win\nSecond win", unbulleted.text)
    }

    @Test
    fun parseInlineRichText_appliesBoldAndItalicSpans() {
        val parsed = JournalRichText.parseInlineRichText("Had a **wonderful** and *peaceful* evening")
        assertEquals("Had a wonderful and peaceful evening", parsed.text)
        assertTrue(parsed.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(parsed.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
    }

    @Test
    fun stripFormattingForSearch_removesMarkersAndBullets() {
        val raw = "- **Morning** *run* felt amazing"
        assertEquals("Morning run felt amazing", JournalRichText.stripFormattingForSearch(raw))
    }
}
