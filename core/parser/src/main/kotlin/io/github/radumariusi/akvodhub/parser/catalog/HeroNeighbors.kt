package io.github.radumariusi.akvodhub.parser.catalog

object HeroNeighbors {
    /** Vecinii pe direcțiile telecomenzii, fără trecere la alt rând pentru stânga/dreapta. */
    fun grid(index: Int, columns: Int, size: Int): List<Int> {
        if (columns <= 0 || index !in 0 until size) return emptyList()
        val column = index % columns
        val nextRow = index - column + columns
        return listOfNotNull(
            (index + 1).takeIf { column < columns - 1 && it < size },
            (index - 1).takeIf { column > 0 },
            (index + columns).coerceAtMost(size - 1).takeIf { nextRow < size },
            (index - columns).takeIf { it >= 0 },
        )
    }
}
