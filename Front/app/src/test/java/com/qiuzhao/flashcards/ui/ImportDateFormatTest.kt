package com.qiuzhao.flashcards.ui

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** Material cards show the server import date (yy/M/d, device time zone) or a dash. */
class ImportDateFormatTest {

    @Test
    fun `import date renders the Figma yy-M-d shape in the device zone`() {
        val instant = Instant.parse("2026-08-28T13:01:28Z")
        val expected = java.time.format.DateTimeFormatter.ofPattern("yy/M/d")
            .format(instant.atZone(java.time.ZoneId.systemDefault()))
        assertEquals(expected, formatImportDate(instant))
    }

    @Test
    fun `null import date renders an honest dash`() {
        assertEquals("—", formatImportDate(null))
    }
}
