package app.sidepeek

/** Selects the best display title exposed by a conversation notification. */
internal object ConversationTitles {
    enum class Source { CONVERSATION, LINE_SUB_TEXT, NOTIFICATION, APP }

    data class Result(val title: String, val source: Source) {
        val isAuthoritativeConversationTitle: Boolean
            get() = source == Source.CONVERSATION || source == Source.LINE_SUB_TEXT
    }

    fun resolve(
        conversationTitle: String?,
        lineSubText: String?,
        notificationTitle: String?,
        appName: String,
    ): Result {
        fun clean(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

        return clean(conversationTitle)?.let { Result(it, Source.CONVERSATION) }
            ?: clean(lineSubText)?.let { Result(it, Source.LINE_SUB_TEXT) }
            ?: clean(notificationTitle)?.let { Result(it, Source.NOTIFICATION) }
            ?: Result(clean(appName).orEmpty(), Source.APP)
    }
}
