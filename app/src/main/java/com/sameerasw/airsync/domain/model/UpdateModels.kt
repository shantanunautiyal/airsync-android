package com.sameerasw.airsync.domain.model

import com.google.gson.annotations.SerializedName

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("name") val name: String,
    @SerializedName("body") val changelog: String,
    @SerializedName("prerelease") val isPrerelease: Boolean,
    @SerializedName("draft") val isDraft: Boolean,
    @SerializedName("published_at") val publishedAt: String,
    @SerializedName("assets") val assets: List<GitHubAsset>
)

data class GitHubAsset(
    @SerializedName("name") val name: String,
    @SerializedName("browser_download_url") val downloadUrl: String,
    @SerializedName("size") val size: Long
)

data class UpdateInfo(
    val release: GitHubRelease,
    val asset: GitHubAsset,
    val currentVersion: String,
    val newVersion: String,
    val isBetaUpdate: Boolean,
    val downloadSize: String
)

enum class UpdateStatus {
    CHECKING,
    NO_UPDATE,
    UPDATE_AVAILABLE,
    DOWNLOADING,
    DOWNLOADED,
    ERROR
}
