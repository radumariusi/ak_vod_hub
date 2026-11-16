package io.github.radumariusi.akvodhub.data.update

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    private val releaseJson =
        """
        {
          "tag_name": "v0.1.12",
          "name": "AK VOD Hub 0.1.12",
          "body": "- Etapa 1: scheletul aplicației",
          "html_url": "https://github.com/radumariusi/ak_vod_hub/releases/tag/v0.1.12",
          "draft": false,
          "prerelease": false,
          "assets": [
            {"name": "notes.txt", "browser_download_url": "https://example.com/notes.txt", "size": 10},
            {"name": "ak-vod-hub-0.1.12.apk", "browser_download_url": "https://example.com/app.apk", "size": 12345}
          ],
          "author": {"login": "someone"}
        }
        """.trimIndent()

    @Test
    fun comparesVersions() {
        assertTrue(AppVersion.isNewer("0.1.12", "0.1.9"))
        assertTrue(AppVersion.isNewer("v0.2.0", "0.1.99"))
        assertTrue(AppVersion.isNewer("0.1.1", "0.1.0-dev"))
        assertFalse(AppVersion.isNewer("0.1.12", "0.1.12"))
        assertFalse(AppVersion.isNewer("0.1.3", "0.1.12"))
        assertEquals(0, AppVersion.compare("0.1", "0.1.0"))
    }

    @Test
    fun parsesReleaseAndPicksApk() {
        val update = UpdateChecker.parseRelease(releaseJson)!!
        assertEquals("0.1.12", update.version)
        assertEquals("https://example.com/app.apk", update.apkUrl)
        assertEquals(12345L, update.apkSize)
        assertEquals("- Etapa 1: scheletul aplicației", update.notes)
    }

    @Test
    fun releaseWithoutApkIsIgnored() {
        assertNull(UpdateChecker.parseRelease("""{"tag_name": "v1.0.0", "assets": []}"""))
    }

    @Test
    fun evaluateOffersOnlyNewerVersions() {
        val checker = UpdateChecker(OkHttpClient(), "owner/repo")
        assertTrue(checker.evaluate(releaseJson, "0.1.5") is UpdateCheckResult.Available)
        assertEquals(UpdateCheckResult.UpToDate, checker.evaluate(releaseJson, "0.1.12"))
    }
}
