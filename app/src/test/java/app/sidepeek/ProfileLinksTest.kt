package app.sidepeek

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileLinksTest {
    private fun msg(pkg: String, title: String, sender: String, text: String, avatar: String? = null, id: String = "x") =
        LineMessage(id = id, pkg = pkg, chatKey = "k", chatTitle = title, sender = sender, text = text, time = 0L, avatarPath = avatar)

    private val threads = "com.instagram.barcelona"
    private val ig = "com.instagram.android"

    @Test fun threadsLikeWithHandleInText() {
        assertEquals("chen__.90", ProfileLinks.handleFor(msg(threads, "1 個新的讚", "1 個新的讚", "chen__.90 說你的回覆讚：除了notistar，我vibe了一個…")))
        assertEquals("shawarma17.c", ProfileLinks.handleFor(msg(threads, "1 個新的讚", "", "shawarma17.c 說你的回覆讚：之後會放到 github")))
    }

    @Test fun threadsHandleInTitle() {
        assertEquals("chen__.90", ProfileLinks.handleFor(msg(threads, "chen__.90", "chen__.90", "說你的回覆讚：除了notistar…")))
    }

    @Test fun instagramActivityAndDm() {
        assertEquals("amy.lu", ProfileLinks.handleFor(msg(ig, "Instagram", "", "amy.lu 和其他 3 人說你的貼文讚")))
        assertEquals("bob_1990", ProfileLinks.handleFor(msg(ig, "bob_1990", "bob_1990", "晚上要不要吃飯")))
    }

    @Test fun displayNameIsNotAHandle() {
        // Bug from the screen recording: title "Ash" (display name) opened the stranger @ash.
        val ash = msg(threads, "Ash", "Ash", "已回覆：好奇卡個位", avatar = "/av/a_1.png", id = "1")
        assertNull(ProfileLinks.handleFor(ash))
        // Same avatar seen in another notification that names the account -> use that.
        val like = msg(threads, "1 個新的讚", "", "chocochoco_ash 說你的回覆讚：除了notistar…", avatar = "/av/a_1.png", id = "2")
        assertEquals("chocochoco_ash", ProfileLinks.handleFor(ash, listOf(like, ash)))
        // A different avatar must not be borrowed.
        val other = msg(threads, "1 個新的讚", "", "someone_else 說你的回覆讚", avatar = "/av/a_2.png", id = "3")
        assertNull(ProfileLinks.handleFor(ash, listOf(other)))
        // Old shared avatar files (saved before content naming) are never used to borrow a handle.
        val oldAsh = msg(threads, "Ash", "Ash", "已回覆：好奇卡個位", avatar = "/av/123456.png", id = "4")
        val oldLike = msg(threads, "1 個新的讚", "", "stevec8213 說你的回覆讚", avatar = "/av/123456.png", id = "5")
        assertNull(ProfileLinks.handleFor(oldAsh, listOf(oldLike)))
    }

    @Test fun noHandle() {
        // Plain DM text must not be mistaken for a handle.
        assertNull(ProfileLinks.handleFor(msg(ig, "小明", "小明", "hello there")))
        // Other apps never get a profile link.
        assertNull(ProfileLinks.handleFor(msg("com.facebook.orca", "bob_1990", "bob_1990", "hi")))
        assertNull(ProfileLinks.handleFor(msg(LineListenerService.LINE_PKG, "amy.lu", "amy.lu", "amy.lu 說你好")))
    }
}
