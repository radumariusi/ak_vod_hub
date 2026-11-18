package io.github.radumariusi.akvodhub.parser.catalog

data class KeysetPage<K : Any, T>(val items: List<T>, val next: K?)

/** Retains a fixed number of pages and one restart key per page already visited. */
class PageWindow<K : Any, T>(
    private val maxPages: Int = 5,
) {
    init {
        require(maxPages >= 2)
    }

    private data class Page<K, T>(val index: Int, val items: List<T>, val next: K?)

    private val starts = mutableListOf<K?>(null)
    private val pages = ArrayDeque<Page<K, T>>()

    val hasEarlier get() = (pages.firstOrNull()?.index ?: 0) > 0
    val hasLater get() = pages.isEmpty() || pages.last().next != null
    val items: List<T> get() = pages.flatMap { it.items }

    suspend fun refresh(fetch: suspend (K?) -> KeysetPage<K, T>) {
        val first = pages.firstOrNull()?.index ?: return move(fetch = fetch)
        var start = starts[first]
        val replacements = ArrayDeque<Page<K, T>>()
        for (index in first until first + pages.size) {
            val result = fetch(start)
            if (result.items.isEmpty() && replacements.isEmpty() && first > 0) {
                val beginning = fetch(null)
                pages.clear()
                starts.clear()
                starts += null
                pages.add(Page(0, beginning.items, beginning.next))
                return
            }
            if (result.items.isNotEmpty() || replacements.isEmpty()) {
                replacements += Page(index, result.items, result.next)
            } else if (result.next == null) {
                replacements.addLast(replacements.removeLast().copy(next = null))
            }
            if (result.next == null) break
            start = result.next
        }
        pages.clear()
        pages.addAll(replacements)
        for (page in replacements.dropLast(1)) starts[page.index + 1] = page.next
    }

    suspend fun move(
        backwards: Boolean = false,
        fetch: suspend (K?) -> KeysetPage<K, T>,
    ) {
        if (backwards && !hasEarlier || !backwards && !hasLater) return
        val index = if (backwards) pages.first().index - 1 else (pages.lastOrNull()?.index ?: -1) + 1
        val start = if (backwards) starts[index] else pages.lastOrNull()?.next
        val result = fetch(start)
        val page = Page(index, result.items, result.next)
        if (index == starts.size) starts += start else starts[index] = start
        if (backwards) {
            pages.addFirst(page)
            if (pages.size > maxPages) pages.removeLast()
        } else if (result.items.isEmpty() && pages.isNotEmpty() && result.next == null) {
            pages.addLast(pages.removeLast().copy(next = null))
        } else {
            pages.addLast(page)
            if (pages.size > maxPages) pages.removeFirst()
        }
    }
}
