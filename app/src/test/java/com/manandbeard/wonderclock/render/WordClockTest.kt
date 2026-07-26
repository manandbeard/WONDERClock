package com.manandbeard.wonderclock.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The letter grid's word positions are hand-placed coordinates, so they get
 * checked against what the sentence form of the same time says.
 */
class WordClockTest {

    // ------------------------------------------------------------ phrases --

    @Test
    fun `on the hour reads o'clock`() {
        assertEquals("it is ten o'clock", WordClock.phrase(10, 0, exact = false))
        assertEquals("it is twelve o'clock", WordClock.phrase(12, 4, exact = false))
        assertEquals("it is twelve o'clock", WordClock.phrase(0, 0, exact = false))
    }

    @Test
    fun `first half of the hour counts past`() {
        assertEquals("it is five past ten", WordClock.phrase(10, 5, exact = false))
        assertEquals("it is a quarter past ten", WordClock.phrase(10, 17, exact = false))
        assertEquals("it is twenty five past ten", WordClock.phrase(10, 25, exact = false))
        assertEquals("it is half past ten", WordClock.phrase(10, 34, exact = false))
    }

    @Test
    fun `second half of the hour counts to the next one`() {
        assertEquals("it is twenty five to eleven", WordClock.phrase(10, 35, exact = false))
        assertEquals("it is a quarter to eleven", WordClock.phrase(10, 45, exact = false))
        assertEquals("it is five to eleven", WordClock.phrase(10, 59, exact = false))
        // 12-hour wrap-around: 23:50 is ten to twelve, not ten to thirteen.
        assertEquals("it is ten to twelve", WordClock.phrase(23, 50, exact = false))
    }

    @Test
    fun `exact mode reads the minutes literally`() {
        assertEquals("it is ten twenty three", WordClock.phrase(10, 23, exact = true))
        assertEquals("it is ten oh five", WordClock.phrase(10, 5, exact = true))
        assertEquals("it is ten o'clock", WordClock.phrase(10, 0, exact = true))
        assertEquals("it is nine fifteen", WordClock.phrase(21, 15, exact = true))
    }

    // --------------------------------------------------------------- grid --

    @Test
    fun `grid is a complete rectangle`() {
        assertEquals(WordClock.ROWS, WordClock.GRID.size)
        WordClock.GRID.forEach { row -> assertEquals(WordClock.COLS, row.length) }
    }

    @Test
    fun `every minute of the day lights valid cells`() {
        for (hour in 0..23) {
            for (minute in 0..59) {
                val lit = WordClock.litCells(hour, minute)
                assertTrue("$hour:$minute lit nothing", lit.isNotEmpty())
                lit.forEach { index ->
                    assertTrue(
                        "$hour:$minute lit out-of-range cell $index",
                        index in 0 until WordClock.ROWS * WordClock.COLS,
                    )
                }
            }
        }
    }

    @Test
    fun `half past ten lights exactly those words`() {
        assertEquals(listOf("IT", "IS", "AM", "HALF", "PAST", "TEN"), litWords(10, 30))
    }

    @Test
    fun `twenty five to eleven lights exactly those words`() {
        // TWENTY and FIVE sit next to each other on row 2, so they read as one run.
        assertEquals(listOf("IT", "IS", "AM", "TWENTYFIVE", "TO", "ELEVEN"), litWords(10, 35))
    }

    @Test
    fun `a quarter past nine lights exactly those words`() {
        assertEquals(
            listOf("IT", "IS", "AM", "A", "QUARTER", "PAST", "NINE"),
            litWords(9, 15),
        )
    }

    @Test
    fun `noon lights twelve o'clock and PM`() {
        assertEquals(listOf("IT", "IS", "PM", "TWELVE", "OCLOCK"), litWords(12, 0))
    }

    @Test
    fun `midnight lights twelve o'clock and AM`() {
        assertEquals(listOf("IT", "IS", "AM", "TWELVE", "OCLOCK"), litWords(0, 2))
    }

    @Test
    fun `each five minute step lights the matching minute word`() {
        val expected = mapOf(
            0 to null,
            5 to "FIVE",
            10 to "TEN",
            15 to "QUARTER",
            20 to "TWENTY",
            25 to "TWENTYFIVE",
            30 to "HALF",
            35 to "TWENTYFIVE",
            40 to "TWENTY",
            45 to "QUARTER",
            50 to "TEN",
            55 to "FIVE",
        )
        expected.forEach { (minute, word) ->
            val words = litWords(3, minute)
            if (word == null) {
                assertTrue("$minute should read o'clock", "OCLOCK" in words)
            } else {
                assertTrue("$minute should light $word, got $words", word in words)
            }
        }
    }

    /**
     * Reads the lit letters back off the grid as words: every maximal run of
     * adjacent lit cells in a row, in reading order.
     */
    private fun litWords(hour: Int, minute: Int): List<String> {
        val lit = WordClock.litCells(hour, minute)
        val words = mutableListOf<String>()
        for (row in 0 until WordClock.ROWS) {
            val builder = StringBuilder()
            for (column in 0 until WordClock.COLS) {
                if (row * WordClock.COLS + column in lit) {
                    builder.append(WordClock.GRID[row][column])
                } else if (builder.isNotEmpty()) {
                    words += builder.toString()
                    builder.setLength(0)
                }
            }
            if (builder.isNotEmpty()) words += builder.toString()
        }
        return words
    }
}
