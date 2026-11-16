package io.github.radumariusi.akvodhub.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VodKindTest {
    @Test
    fun detectsXtreamMovieAndSeriesUrls() {
        assertEquals(VodKind.MOVIE, VodKind.fromUrl("http://host:8080/movie/user/pass/123.mkv"))
        assertEquals(VodKind.EPISODE, VodKind.fromUrl("http://host:8080/series/user/pass/456.mp4"))
    }

    @Test
    fun ignoresLiveStreams() {
        assertNull(VodKind.fromUrl("http://host:8080/live/user/pass/789.ts"))
    }
}
