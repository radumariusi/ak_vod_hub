package io.github.radumariusi.akvodhub.parser.player

import io.github.radumariusi.akvodhub.parser.catalog.EpisodeEntry
import io.github.radumariusi.akvodhub.parser.catalog.EpisodeMerge
import io.github.radumariusi.akvodhub.parser.match.SourceInfo
import io.github.radumariusi.akvodhub.parser.title.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLogicTest {
    private val uhdEn = SourceInfo(1, VideoQuality.UHD, languages = listOf("en"))
    private val uhdDe = SourceInfo(2, VideoQuality.UHD, languages = listOf("de"))
    private val fhdRo = SourceInfo(3, VideoQuality.FHD, languages = listOf("ro"))
    private val fhdEn = SourceInfo(4, VideoQuality.FHD, languages = listOf("en"))
    private val hdMulti = SourceInfo(5, VideoQuality.HD, languages = listOf("mul"))
    private val all = listOf(fhdEn, hdMulti, uhdDe, fhdRo, uhdEn)

    @Test
    fun `implicit sursele EN RO si MULTI vin primele`() {
        val order = SourceProfile().order(all, listOf("en")).map { it.id }
        assertEquals(listOf(1L, 4L, 3L, 5L, 2L), order)
    }

    @Test
    fun `4K RO si MULTI au prioritate fata de 1080p EN`() {
        val uhdRo = SourceInfo(7, VideoQuality.UHD, languages = listOf("ro"))
        val uhdMulti = SourceInfo(8, VideoQuality.UHD, languages = listOf("mul"))
        assertEquals(listOf(7L, 8L, 4L, 2L), SourceProfile().order(listOf(uhdDe, fhdEn, uhdMulti, uhdRo)).map { it.id })
    }

    @Test
    fun `profilul 4K EN altfel 1080p RO`() {
        val profile = SourceProfile(listOf(SourceRule(VideoQuality.UHD, "en"), SourceRule(VideoQuality.FHD, "ro")))
        assertEquals(listOf(1L, 3L, 2L, 4L, 5L), profile.order(all).map { it.id })
        assertEquals("4K EN, altfel 1080p RO", profile.describe())
    }

    @Test
    fun `limba RO oricare calitate, MULTI dupa potrivirea exacta`() {
        val profile = SourceProfile(listOf(SourceRule(null, "ro"), SourceRule(null, "it")))
        assertEquals(listOf(3L, 5L, 2L, 1L, 4L), profile.order(all).map { it.id })
        val italian = SourceProfile(listOf(SourceRule(null, "it")))
        assertEquals(5L, italian.order(all).first().id)
    }

    @Test
    fun `profilul se salveaza si se citeste`() {
        val profile = SourceProfile(listOf(SourceRule(VideoQuality.UHD, "en"), SourceRule(null, "ro")))
        assertEquals("UHD:en;*:ro", profile.encode())
        assertEquals(profile, SourceProfile.decode(profile.encode()))
        assertEquals(SourceProfile(), SourceProfile.decode(""))
        assertEquals(SourceProfile(), SourceProfile.decode("*:*;garbage"))
    }

    @Test
    fun `rezerva trece la urmatoarea sursa si se opreste la final`() {
        val q = FallbackQueue(listOf(1, 2, 3))
        assertEquals(1L, q.current)
        assertEquals(2L, q.fail("HTTP 404"))
        assertEquals(2, q.position)
        assertEquals(3L, q.fail("nu răspunde"))
        assertFalse(q.hasMore)
        assertNull(q.fail("format"))
        assertEquals(listOf("HTTP 404", "nu răspunde", "format"), q.failures.map { it.reason })
        assertEquals(1L, q.reset())
    }

    @Test
    fun `sursa aleasa de utilizator e prima, apoi restul de la inceput`() {
        val q = FallbackQueue(listOf(1, 2, 3), start = 3)
        assertEquals(listOf(3L, 1L, 2L), q.candidates)
        assertEquals(1L, q.fail("x"))
        assertTrue(q.switchTo(3))
        assertEquals(1L, q.fail("y"))
        assertEquals(2L, q.fail("z"))
        assertNull(q.fail("w"))
    }

    @Test
    fun `reluare si vazut`() {
        assertFalse(Resume.shouldResume(10_000, 6_000_000))
        assertTrue(Resume.shouldResume(600_000, 6_000_000))
        assertTrue(Resume.isFinished(5_700_000, 6_000_000))
        assertTrue(Resume.isFinished(5_900_000, 6_000_000))
        assertFalse(Resume.isFinished(100, 0))
        assertEquals("12:34", Resume.format(754_000))
        assertEquals("1:02:05", Resume.format(3_725_000))
    }

    @Test
    fun `urmatorul episod trece si la sezonul urmator`() {
        fun e(
            s: Int?,
            n: Int?,
        ) = EpisodeEntry(s, n, "Show S0${s}E0$n", SourceInfo((s ?: 0) * 100L + (n ?: 0), VideoQuality.FHD))
        val seasons = EpisodeMerge.merge(listOf(e(1, 1), e(1, 2), e(2, 1), e(0, 1), e(1, null)))
        assertEquals(2, EpisodeOrder.next(seasons, 1, 1)?.number)
        val next = EpisodeOrder.next(seasons, 1, 2)
        assertEquals(2, next?.season)
        assertEquals(1, next?.number)
        assertNull(EpisodeOrder.next(seasons, 2, 1))
        assertNull(EpisodeOrder.next(seasons, 1, null))
        assertEquals(2, EpisodeOrder.find(seasons, 1, 2)?.number)
        assertEquals(
            next,
            EpisodeOrder.resumeOrNext(seasons, null, null, 1, 1, true) {
                it.season == 1 && it.number == 2
            },
        )
        assertNull(EpisodeOrder.resumeOrNext(seasons, null, null, 2, 1, true) { false })
    }
}
