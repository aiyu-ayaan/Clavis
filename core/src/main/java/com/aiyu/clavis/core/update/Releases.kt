package com.aiyu.clavis.core.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Pure release logic: no Android, no network, no clock. The release workflow names versions `1.0.1-alpha.1`,
 * `1.0.1-beta.1` and `1.0.1` and attaches `Clavis-<version>.apk` (see `.github/workflows/release.yml`).
 */
data class Version(val major: Int, val minor: Int, val patch: Int, val stage: Int, val stageNumber: Int) : Comparable<Version> {
    override fun compareTo(other: Version) =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch }, { it.stage }, { it.stageNumber })

    companion object {
        const val ALPHA = 0
        const val BETA = 1
        const val STABLE = 2
        private val PATTERN = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta)\.(\d+))?$""", RegexOption.IGNORE_CASE)

        /** Only what the release workflow produces; anything else is null, because a guess installs the wrong APK. */
        fun parse(text: String?): Version? {
            val m = PATTERN.matchEntire(text?.trim().orEmpty()) ?: return null
            val (major, minor, patch, label, number) = m.destructured
            return Version(
                major.toInt(), minor.toInt(), patch.toInt(),
                stage = when (label.lowercase()) { "alpha" -> ALPHA; "beta" -> BETA; else -> STABLE },
                stageNumber = number.toIntOrNull() ?: Int.MAX_VALUE, // a stable beats `-beta.9` of the same version
            )
        }
    }
}


data class ReleaseAsset(val name: String, val url: String, val size: Long, val sha256: String? = null)
data class Release(val version: Version, val name: String, val notes: String, val assets: List<ReleaseAsset>)

object Releases {
    /** A constant, not a setting: this project signs the builds, and Android refuses an update signed by another key. */
    const val REPOSITORY = "aiyu-ayaan/Clavis"
    const val API = "https://api.github.com/repos/$REPOSITORY/releases?per_page=30"

    /** Newest release at least as steady as the installed build and newer than it, by version, never publish date. */
    fun pick(releases: List<Release>, installed: Version?): Release? =
        releases.filter { r -> installed == null || (r.version.stage >= installed.stage && r.version > installed) }.maxByOrNull { it.version }

    /** `Clavis-<v>.apk`. A release without one (its Android job failed) is not an offer. */
    fun apkFor(release: Release): ReleaseAsset? =
        release.assets.firstOrNull { it.name.startsWith("Clavis-") && it.name.endsWith(".apk", ignoreCase = true) }

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): List<Release> = json.decodeFromString<List<ReleaseDto>>(body).mapNotNull { r ->
        if (r.draft) return@mapNotNull null
        val version = Version.parse(r.tag) ?: return@mapNotNull null
        Release(
            version, (r.name?.ifBlank { null } ?: r.tag).removePrefix("v"), r.body.orEmpty(),
            r.assets.map { ReleaseAsset(it.name, it.url, it.size, it.digest?.removePrefix("sha256:")?.lowercase()) },
        )
    }
}

@Serializable private class ReleaseDto(
    @SerialName("tag_name") val tag: String = "",
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val assets: List<AssetDto> = emptyList(),
)

@Serializable private class AssetDto(
    val name: String = "",
    @SerialName("browser_download_url") val url: String = "",
    val size: Long = 0,
    val digest: String? = null,
)
