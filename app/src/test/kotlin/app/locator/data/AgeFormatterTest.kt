package app.locator.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AgeFormatterTest {
    private val min = 60_000L

    @Test fun underAMinuteIsJustNow() = assertEquals(Age.JustNow, AgeFormatter.age(100 * min, 100 * min - 59_000))
    @Test fun futureTimestampIsJustNow() = assertEquals(Age.JustNow, AgeFormatter.age(0, 5 * min))
    @Test fun minutes() = assertEquals(Age.Minutes(5), AgeFormatter.age(10 * min, 5 * min))
    @Test fun hours() = assertEquals(Age.Hours(2), AgeFormatter.age(130 * min, 0))
    @Test fun days() = assertEquals(Age.Days(3), AgeFormatter.age(3 * 24 * 60 * min + 1, 0))
}
