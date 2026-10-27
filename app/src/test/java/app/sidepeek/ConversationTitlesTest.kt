package app.sidepeek

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationTitlesTest {
    @Test
    fun conversationTitleWins() {
        val result = ConversationTitles.resolve(
            conversationTitle = "家庭群組",
            lineSubText = "LINE 群組名稱",
            notificationTitle = "小明",
            appName = "LINE",
        )

        assertEquals("家庭群組", result.title)
        assertEquals(ConversationTitles.Source.CONVERSATION, result.source)
        assertTrue(result.isAuthoritativeConversationTitle)
    }

    @Test
    fun lineSubTextReplacesLatestSender() {
        val result = ConversationTitles.resolve(null, "週末聚餐群組", "群組成員甲", "LINE")

        assertEquals("週末聚餐群組", result.title)
        assertEquals(ConversationTitles.Source.LINE_SUB_TEXT, result.source)
        assertTrue(result.isAuthoritativeConversationTitle)
    }

    @Test
    fun notificationTitleRemainsFallbackForLegacyNotifications() {
        val result = ConversationTitles.resolve("  ", null, "王小明", "LINE")

        assertEquals("王小明", result.title)
        assertEquals(ConversationTitles.Source.NOTIFICATION, result.source)
    }
}
