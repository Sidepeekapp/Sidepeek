package app.sidepeek

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatTitleMatchTest {

    private val chats = mapOf(
        "line|a" to "週末烤肉群組",
        "line|b" to "小明",
        "line|c" to "公司專案討論區第二組",
    )

    @Test fun exactName() = assertEquals("line|b", ChatTitleMatch.match(listOf("返回", "小明"), chats))

    @Test fun memberCountDropped() =
        assertEquals("line|a", ChatTitleMatch.match(listOf("週末烤肉群組 (5)"), chats))

    @Test fun fullWidthCountAndSpaces() =
        assertEquals("line|a", ChatTitleMatch.match(listOf("  週末烤肉群組（12）"), chats))

    @Test fun truncatedPrefix() =
        assertEquals("line|c", ChatTitleMatch.match(listOf("公司專案討論…"), chats))

    @Test fun shortTruncationIgnored() = assertNull(ChatTitleMatch.match(listOf("公司…"), chats))

    @Test fun noMatch() = assertNull(ChatTitleMatch.match(listOf("聊天", "搜尋"), chats))

    @Test fun twoMatchesIsUnsure() = assertNull(ChatTitleMatch.match(listOf("小明", "週末烤肉群組"), chats))

    @Test fun halfWidthDigits() = assertEquals(
        "line|x", ChatTitleMatch.match(listOf("ＡＢＣ讀書會"), mapOf("line|x" to "ABC讀書會")),
    )

    @Test fun substringIsNotAMatch() = assertNull(ChatTitleMatch.match(listOf("小明的家"), chats))
}
