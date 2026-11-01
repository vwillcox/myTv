package com.vwillcox.mytv.data

import java.text.Normalizer

/**
 * Finding titles by name as they're typed, and which keys could carry a search on. Names and searches are compared
 * folded: lower case, without accents, and with every run of anything but letters and digits as one space, so
 * "Amélie" is found by "amelie" and "Spider-Man" by "spider man".
 */
object TitleSearch {
    private val marks = Regex("\\p{M}+")
    private val gaps = Regex("[^\\p{L}\\p{N}]+")

    private fun fold(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(marks, "").lowercase().replace(gaps, " ")

    /** A name to search, folded and with a space each side, so a word's start and end are always found. */
    fun name(s: String): String = " ${fold(s).trim()} "

    /** What's typed, folded. A space typed at the end is kept, as the end of a word. */
    fun query(s: String): String = fold(s).trimStart()

    /** How well [name] matches [query]: 0 at its start, 1 at a word's start, 2 anywhere in it; null if it doesn't. */
    fun rank(name: String, query: String): Int? {
        if (query.isBlank()) return null
        val at = name.indexOf(query)
        return when {
            at < 0 -> null
            at == 1 -> 0
            name.contains(" $query") -> 1
            else -> 2
        }
    }

    /** What in [items] (each with its [name]) matches [query], best match first and otherwise in the order given. */
    fun <T> find(query: String, items: List<Pair<String, T>>): List<T> =
        items.mapNotNull { (n, item) -> rank(n, query)?.let { it to item } }.sortedBy { it.first }.map { it.second }

    /**
     * The characters that could come next in [query] and still match one of [names]: a letter, a digit, or ' ' to
     * start a new word. Finishing a name doesn't count as a space.
     */
    fun next(query: String, names: List<String>): Set<Char> {
        val out = HashSet<Char>()
        if (query.isEmpty()) return out
        for (n in names) {
            var at = n.indexOf(query)
            while (at >= 0) {
                val after = at + query.length
                if (after < n.lastIndex) out += n[after]
                at = n.indexOf(query, at + 1)
            }
        }
        return out
    }
}
