package app.sidepeek

import app.sidepeek.GroupPicture.THUMB
import app.sidepeek.GroupPicture.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupPictureTest {
    /** A thumbnail: [rgb] in the middle, [corner] (e.g. transparent after a circle crop) in the corners. */
    private fun pic(rgb: Int, corner: Int = 0xFF000000.toInt() or rgb) = IntArray(THUMB * THUMB) { i ->
        val x = i % THUMB; val y = i / THUMB
        val dx = x - 5.5; val dy = y - 5.5
        if (dx * dx + dy * dy > 36) corner else 0xFF000000.toInt() or rgb
    }

    private val red = pic(0xDC2626)
    private val blue = pic(0x2563EB)

    @Test fun firstMessageIsUnknown() =
        assertEquals(Verdict.UNKNOWN, GroupPicture.judge(null, null, "阿明", red, null))

    @Test fun sameSenderTellsNothing() =
        assertEquals(Verdict.UNKNOWN, GroupPicture.judge("阿明", red, "阿明", blue, null))

    @Test fun samePictureFromTwoPeopleIsTheChats() =
        assertEquals(Verdict.CHAT, GroupPicture.judge("小華", red, "阿明", red, null))

    @Test fun pictureChangingWithSenderIsTheSenders() =
        assertEquals(Verdict.SENDER, GroupPicture.judge("小華", red, "阿明", blue, null))

    @Test fun personIconDecidesAtOnce() {
        assertEquals(Verdict.SENDER, GroupPicture.judge(null, null, "阿明", red, red))
        assertEquals(Verdict.CHAT, GroupPicture.judge(null, null, "阿明", red, blue))
    }

    // LINE's Person icon and large icon of the same member differ slightly (size, circle crop).
    @Test fun croppedOrRescaledCopyIsTheSamePicture() {
        val circleCropped = pic(0xDC2626, corner = 0)
        val rescaled = pic(0xD02A2A)
        assertTrue(GroupPicture.same(red, circleCropped))
        assertTrue(GroupPicture.same(red, rescaled))
        assertEquals(Verdict.SENDER, GroupPicture.judge(null, null, "Test Bot", circleCropped, rescaled))
    }

    @Test fun differentPicturesAreNotTheSame() = assertFalse(GroupPicture.same(red, blue))
}
