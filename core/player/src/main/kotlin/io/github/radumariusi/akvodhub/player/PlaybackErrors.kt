package io.github.radumariusi.akvodhub.player

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource

/** Motivul pentru care o sursă nu merge, pe înțelesul utilizatorului. */
@OptIn(UnstableApi::class)
object PlaybackErrors {
    fun describe(e: PlaybackException): String {
        httpCode(e)?.let { return "serverul a răspuns HTTP $it" }
        return when (e.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "nu mă pot conecta la server"

            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "serverul nu răspunde"

            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "serverul a refuzat linkul"

            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "fișierul nu există"

            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> "serverul nu a trimis un video"

            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "format de fișier nesuportat"

            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            -> "fișier deteriorat"

            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            -> "decodorul TV-ului nu pornește"

            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "codec nesuportat de acest TV"

            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "calitate peste puterea acestui TV"

            PlaybackException.ERROR_CODE_DECODING_FAILED -> "eroare la decodare"

            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
            -> "ieșirea audio nu pornește"

            else -> "eroare ${e.errorCodeName}"
        }
    }

    /** Eroare de rețea / server (merită o reîncercare a aceleiași surse dacă mergea deja). */
    fun isNetwork(e: PlaybackException): Boolean = e.errorCode in 2000 until 3000

    private fun httpCode(e: Throwable): Int? {
        var cause: Throwable? = e
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) return cause.responseCode
            cause = cause.cause
        }
        return null
    }
}
