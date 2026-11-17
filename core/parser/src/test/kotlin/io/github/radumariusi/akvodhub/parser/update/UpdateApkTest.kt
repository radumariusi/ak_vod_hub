package io.github.radumariusi.akvodhub.parser.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateApkTest {
    private val version = "0.1.37"
    private val universal = "ak-vod-hub-$version.apk"
    private val arm32 = "ak-vod-hub-$version-armeabi-v7a.apk.bin"
    private val arm64 = "ak-vod-hub-$version-arm64-v8a.apk.bin"
    private val x64 = "ak-vod-hub-$version-x86_64.apk.bin"
    private val assets = setOf(arm32, x64, universal, arm64)

    @Test
    fun usesDeviceAbiOrderAndAdvertisedFallbacks() {
        assertEquals(arm64, UpdateApk.select(version, listOf("arm64-v8a", "armeabi-v7a"), assets))
        assertEquals(arm32, UpdateApk.select(version, listOf("armeabi-v7a", "arm64-v8a"), assets))
        assertEquals(arm32, UpdateApk.select(version, listOf("arm64-v8a", "armeabi-v7a"), assets - arm64))
        assertEquals(x64, UpdateApk.select(version, listOf("x86_64"), assets))
    }

    @Test
    fun acceptsOnlyKnownCompatibleAssetsOrTheExactUniversalName() {
        assertEquals(universal, UpdateApk.select(version, listOf("arm64-v8a"), setOf(universal)))
        assertEquals(universal, UpdateApk.select(version, listOf("x86", "arm64-v8a"), setOf(universal)))
        assertNull(UpdateApk.select(version, listOf("x86"), assets))
        assertNull(UpdateApk.select(version, emptyList(), assets))
        assertNull(UpdateApk.select(version, listOf("arm64-v8a"), setOf(arm32, x64, "unrelated.apk")))
        assertNull(UpdateApk.select("0.1.38", listOf("arm64-v8a"), assets))
    }

    @Test
    fun legacyUpdaterHasOnlyOneApkCandidateEvenWhenVariantsComeFirst() {
        assertEquals(listOf(universal), assets.filter { it.endsWith(".apk", ignoreCase = true) })
    }
}
