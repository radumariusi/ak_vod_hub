package io.github.radumariusi.akvodhub.data.update

/**
 * Compară versiuni de forma `0.1.42` (prefixul `v` și sufixe ca `-dev` sunt ignorate).
 */
object AppVersion {
    fun parts(version: String): List<Int> =
        version
            .trim()
            .removePrefix("v")
            .substringBefore('-')
            .split('.')
            .map { it.toIntOrNull() ?: 0 }

    fun compare(
        a: String,
        b: String,
    ): Int {
        val pa = parts(a)
        val pb = parts(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val diff = pa.getOrElse(i) { 0 }.compareTo(pb.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    fun isNewer(
        candidate: String,
        current: String,
    ): Boolean = compare(candidate, current) > 0
}
