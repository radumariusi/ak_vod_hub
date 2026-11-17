package io.github.radumariusi.akvodhub.parser.update

object UpdateApk {
    private val packagedAbis = setOf("armeabi-v7a", "arm64-v8a", "x86_64")

    /**
     * Contractul din GitHub Releases. Universalul este singurul asset cu extensia .apk,
     * astfel încât versiunile vechi ale updaterului îl găsesc indiferent de ordinea asseturilor.
     */
    fun select(version: String, supportedAbis: List<String>, assetNames: Set<String>): String? {
        val prefix = "ak-vod-hub-$version"
        for (abi in supportedAbis) {
            if (abi !in packagedAbis) continue
            val name = "$prefix-$abi.apk.bin"
            if (name in assetNames) return name
        }
        return "$prefix.apk".takeIf { it in assetNames && supportedAbis.any(packagedAbis::contains) }
    }
}
