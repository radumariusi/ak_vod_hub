package io.github.radumariusi.akvodhub.parser.player

/** O comandă discretă se execută la prima apăsare, o singură dată. Derularea poate folosi repetările. */
object PlayerKeyPress {
    fun activate(keyDown: Boolean, repeatCount: Int): Boolean = keyDown && repeatCount == 0
}
