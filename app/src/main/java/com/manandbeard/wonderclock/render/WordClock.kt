package com.manandbeard.wonderclock.render

/**
 * The time in English words, both as a sentence and as the classic 11x10
 * letter matrix where the relevant words light up.
 */
internal object WordClock {

    private val HOURS = listOf(
        "twelve", "one", "two", "three", "four", "five",
        "six", "seven", "eight", "nine", "ten", "eleven",
    )

    private val SMALL = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
        "seventeen", "eighteen", "nineteen",
    )

    private val TENS = mapOf(20 to "twenty", 30 to "thirty", 40 to "forty", 50 to "fifty")

    private fun hourWord(hour24: Int): String = HOURS[((hour24 % 12) + 12) % 12]

    private fun minuteWord(minute: Int): String = when {
        minute < 20 -> SMALL[minute]
        else -> {
            val tens = TENS[(minute / 10) * 10] ?: ""
            val ones = minute % 10
            if (ones == 0) tens else "$tens ${SMALL[ones]}"
        }
    }

    /**
     * "it is half past ten". With [exact] the sentence reads the minutes out
     * literally instead of rounding down to the nearest five.
     */
    fun phrase(hour24: Int, minute: Int, exact: Boolean): String {
        if (exact) {
            return when {
                minute == 0 -> "it is ${hourWord(hour24)} o'clock"
                minute < 10 -> "it is ${hourWord(hour24)} oh ${SMALL[minute]}"
                else -> "it is ${hourWord(hour24)} ${minuteWord(minute)}"
            }
        }

        val rounded = (minute / 5) * 5
        if (rounded == 0) return "it is ${hourWord(hour24)} o'clock"

        val toNextHour = rounded > 30
        val hour = if (toNextHour) hour24 + 1 else hour24
        val amount = when (rounded) {
            5, 55 -> "five"
            10, 50 -> "ten"
            15, 45 -> "a quarter"
            20, 40 -> "twenty"
            25, 35 -> "twenty five"
            else -> "half"
        }
        val direction = if (toNextHour) "to" else "past"
        return "it is $amount $direction ${hourWord(hour)}"
    }

    // -------------------------------------------------------- letter grid --

    const val COLS = 11
    const val ROWS = 10

    /** Row-major letters of the matrix; every row is exactly [COLS] long. */
    val GRID: List<String> = listOf(
        "ITLISASAMPM",
        "ACQUARTERDC",
        "TWENTYFIVEX",
        "HALFSTENFTO",
        "PASTERUNINE",
        "ONESIXTHREE",
        "FOURFIVETWO",
        "EIGHTELEVEN",
        "SEVENTWELVE",
        "TENSEOCLOCK",
    )

    private fun run(row: Int, col: Int, length: Int): List<Int> =
        (0 until length).map { row * COLS + col + it }

    private val IT = run(0, 0, 2)
    private val IS = run(0, 3, 2)
    private val AM = run(0, 7, 2)
    private val PM = run(0, 9, 2)
    private val A = run(1, 0, 1)
    private val QUARTER = run(1, 2, 7)
    private val TWENTY = run(2, 0, 6)
    private val FIVE_MIN = run(2, 6, 4)
    private val HALF = run(3, 0, 4)
    private val TEN_MIN = run(3, 5, 3)
    private val TO = run(3, 9, 2)
    private val PAST = run(4, 0, 4)
    private val OCLOCK = run(9, 5, 6)

    private val HOUR_CELLS = listOf(
        run(8, 5, 6), // twelve
        run(5, 0, 3), // one
        run(6, 8, 3), // two
        run(5, 6, 5), // three
        run(6, 0, 4), // four
        run(6, 4, 4), // five
        run(5, 3, 3), // six
        run(8, 0, 5), // seven
        run(7, 0, 5), // eight
        run(4, 7, 4), // nine
        run(9, 0, 3), // ten
        run(7, 5, 6), // eleven
    )

    /** Indices (row * [COLS] + col) of the letters that should be lit. */
    fun litCells(hour24: Int, minute: Int): Set<Int> {
        val lit = HashSet<Int>(48)
        lit += IT
        lit += IS

        val rounded = (minute / 5) * 5
        val toNextHour = rounded > 30
        val displayHour = if (toNextHour) hour24 + 1 else hour24

        when (rounded) {
            5, 55 -> lit += FIVE_MIN
            10, 50 -> lit += TEN_MIN
            15, 45 -> { lit += A; lit += QUARTER }
            20, 40 -> lit += TWENTY
            25, 35 -> { lit += TWENTY; lit += FIVE_MIN }
            30 -> lit += HALF
        }

        if (rounded == 0) {
            lit += OCLOCK
        } else {
            lit += if (toNextHour) TO else PAST
        }

        lit += HOUR_CELLS[((displayHour % 12) + 12) % 12]
        lit += if ((((displayHour % 24) + 24) % 24) < 12) AM else PM

        return lit
    }
}
