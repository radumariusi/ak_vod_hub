package io.github.radumariusi.akvodhub.parser.title

/** Calitate din dimensiunile pistei video sau dintr-o valoare explicit declarată de furnizor. */
object VideoResolution {
    private val dimensions = Regex("""(\d{2,5})\s*[x×]\s*(\d{2,5})""", RegexOption.IGNORE_CASE)

    fun quality(width: Int, height: Int): VideoQuality? {
        if (width !in 100..16384 || height !in 100..16384) return null
        return when {
            width >= 3800 || height >= 2000 -> VideoQuality.UHD
            width >= 1900 || height >= 1000 -> VideoQuality.FHD
            width >= 1260 || height >= 700 -> VideoQuality.HD
            else -> VideoQuality.SD
        }
    }

    fun declared(label: String?): VideoQuality? {
        val value = label?.trim()?.uppercase() ?: return null
        dimensions.matchEntire(value)?.let {
            return quality(it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }
        return TitleTags.quality[value.replace(" ", "")]
    }
}
