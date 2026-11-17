package io.github.radumariusi.akvodhub.parser.player

/** Semnal de proces pentru lucrările de metadate; nu se persistă și nu controlează playerul. */
object PlaybackActivityState {
    @Volatile var active: Boolean = false
}
