package io.github.radumariusi.akvodhub.parser.catalog

object LocalizedOverview {
    private const val TTL = 30L * 24 * 60 * 60 * 1000

    fun clean(value: String?): String? = value?.trim()?.ifEmpty { null }

    fun choose(romanian: String?, english: String?): String? = clean(romanian) ?: clean(english)

    /** Inclusiv traducerea absentă se memorează, dar poate fi verificată din nou după 30 zile. */
    fun isCached(fetchedAt: Long, now: Long): Boolean = fetchedAt > 0 && now >= fetchedAt && now - fetchedAt < TTL
}
