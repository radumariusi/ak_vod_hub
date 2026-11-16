package io.github.radumariusi.akvodhub.data.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Doar câmpurile de care avem nevoie din API-ul GitHub `releases/latest`. */
@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0,
)

/** Un update disponibil, gata de descărcat. */
data class AppUpdate(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    val pageUrl: String?,
)
