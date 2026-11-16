package app.locator.data

sealed interface Age {
    data object JustNow : Age
    data class Minutes(val n: Int) : Age
    data class Hours(val n: Int) : Age
    data class Days(val n: Int) : Age
}

object AgeFormatter {
    fun age(nowMillis: Long, thenMillis: Long): Age {
        val minutes = (maxOf(0L, nowMillis - thenMillis) / 60_000L)
        return when {
            minutes < 1 -> Age.JustNow
            minutes < 60 -> Age.Minutes(minutes.toInt())
            minutes < 24 * 60 -> Age.Hours((minutes / 60).toInt())
            else -> Age.Days((minutes / (24 * 60)).toInt())
        }
    }
}
