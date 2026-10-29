package app.sidepeek

import app.sidepeek.GroupPicture.Verdict
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupPictureTest {
    @Test fun firstMessageIsUnknown() =
        assertEquals(Verdict.UNKNOWN, GroupPicture.judge(null, null, "阿明", 1, null))

    @Test fun sameSenderTellsNothing() =
        assertEquals(Verdict.UNKNOWN, GroupPicture.judge("阿明", 1, "阿明", 2, null))

    @Test fun samePictureFromTwoPeopleIsTheChats() =
        assertEquals(Verdict.CHAT, GroupPicture.judge("小華", 7, "阿明", 7, null))

    @Test fun pictureChangingWithSenderIsTheSenders() =
        assertEquals(Verdict.SENDER, GroupPicture.judge("小華", 7, "阿明", 8, null))

    @Test fun personIconDecidesAtOnce() {
        assertEquals(Verdict.SENDER, GroupPicture.judge(null, null, "阿明", 5, 5))
        assertEquals(Verdict.CHAT, GroupPicture.judge(null, null, "阿明", 5, 6))
    }
}
