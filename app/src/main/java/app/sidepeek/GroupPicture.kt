package app.sidepeek

/**
 * LINE group chats: is the notification's large icon the chat's own picture or the latest sender's?
 * Ordinary groups put the sender there, communities (社群) put the community's picture there, and
 * the notification does not say which. Judge it from what we see over a few messages.
 */
object GroupPicture {
    enum class Verdict { CHAT, SENDER, UNKNOWN }

    /**
     * @param prevSender / prevIcon the sender and large-icon fingerprint of the previous message in this chat
     * @param personIcon fingerprint of the sender's own Person icon, when LINE gave one
     */
    fun judge(prevSender: String?, prevIcon: Int?, sender: String, icon: Int, personIcon: Int?): Verdict = when {
        // The sender's own picture came along: the large icon is either that same picture or something else.
        personIcon != null -> if (personIcon == icon) Verdict.SENDER else Verdict.CHAT
        prevSender == null || prevIcon == null || prevSender == sender -> Verdict.UNKNOWN
        // Two different people, one picture: it belongs to the chat.
        prevIcon == icon -> Verdict.CHAT
        else -> Verdict.SENDER
    }
}
