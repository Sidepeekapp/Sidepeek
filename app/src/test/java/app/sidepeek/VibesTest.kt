package app.sidepeek

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VibesTest {
    @Test fun pressesBecomeWaitOnOffPattern() {
        // first press at 0–200, gap 100, second press 300–350
        assertArrayEquals(longArrayOf(0, 200, 100, 50), Vibes.toPattern(listOf(0L to 200L, 300L to 350L)))
    }

    @Test fun shortPressIsStretchedAndOverlapIsMerged() {
        // a 5 ms tap becomes MIN_ON_MS; the next press starting inside it starts after it
        assertArrayEquals(longArrayOf(0, Vibes.MIN_ON_MS, 0, 60), Vibes.toPattern(listOf(0L to 5L, 20L to 100L)))
    }

    @Test fun cutAtMax() {
        assertArrayEquals(longArrayOf(2900, 100), Vibes.toPattern(listOf(2900L to 5000L, 3100L to 3200L)))
    }

    @Test fun nothingPressed() {
        assertArrayEquals(LongArray(0), Vibes.toPattern(emptyList()))
    }

    @Test fun parseRoundTrip() {
        val p = longArrayOf(0, 330, 160, 100)
        assertArrayEquals(p, Vibes.parse(Vibes.encode(p)))
    }

    @Test fun parseRejectsBroken() {
        assertNull(Vibes.parse(null))
        assertNull(Vibes.parse(""))
        assertNull(Vibes.parse("0"))
        assertNull(Vibes.parse("0,abc"))
        assertNull(Vibes.parse("0,-5"))
    }
}
