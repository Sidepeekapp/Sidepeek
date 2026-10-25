package app.sidepeek

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkExtractorTest {
    private fun ex(s: String) = LinkExtractor.extract(s)

    @Test fun userNamesThatLookLikeDomainsAreNotLinks() {
        assertEquals(emptyList<String>(), ex("sam.fu.tw 說你的回覆讚：之後會放到github 也不打算收錢"))
        assertEquals(emptyList<String>(), ex("amy.lu 開始追蹤你"))
        assertEquals(emptyList<String>(), ex("anny.0923、berit_sto.re 和其他 3 人說讚"))
        assertEquals(emptyList<String>(), ex("@sam.fu.tw 標註了你"))
        assertEquals(emptyList<String>(), ex("寄到 someone@gmail.com 就好"))
    }

    @Test fun notLinks() {
        assertEquals(emptyList<String>(), ex("版本 3.5.17 已更新"))
        assertEquals(emptyList<String>(), ex("附件 report.pdf 和 photo.jpg"))
        assertEquals(emptyList<String>(), ex("用 Node.js 寫的"))
    }

    @Test fun explicitLinks() {
        assertEquals(listOf("https://sam.fu.tw"), ex("我的網站 https://sam.fu.tw 歡迎"))
        assertEquals(listOf("https://github.com/x/y"), ex("看這個https://github.com/x/y很好"))
        assertEquals(listOf("http://example.tw/a?b=1"), ex("(http://example.tw/a?b=1)"))
        assertEquals(listOf("https://www.youtube.com/watch?v=abc"), ex("https://www.youtube.com/watch?v=abc。"))
    }

    @Test fun wwwAndBareDomains() {
        assertEquals(listOf("https://www.sam.fu.tw"), ex("網址 www.sam.fu.tw"))
        assertEquals(listOf("https://google.com"), ex("去 google.com 查"))
        assertEquals(listOf("https://pchome.com.tw"), ex("pchome.com.tw 有特價"))
        assertEquals(listOf("https://shop.example.tw/item/9"), ex("shop.example.tw/item/9 這個"))
    }

    @Test fun duplicatesCollapse() {
        assertEquals(listOf("https://google.com"), ex("google.com 跟 https://google.com 和 http://google.com/"))
        assertEquals(
            listOf("https://a.com/1", "https://b.com/2", "https://c.com/3"),
            ex("https://a.com/1 https://b.com/2 https://c.com/3 https://d.com/4"),
        )
    }

    @Test fun testNotificationContent() {
        assertEquals(listOf("https://example.com/bbq"), ex("確定！地點在這 https://example.com/bbq"))
        assertEquals(emptyList<String>(), ex("食譜可以參考 chef.lu.tw 的分享"))
        assertEquals(emptyList<String>(), ex("我負責買肉和飲料，總共 1,800 元，一人 450"))
    }
}
