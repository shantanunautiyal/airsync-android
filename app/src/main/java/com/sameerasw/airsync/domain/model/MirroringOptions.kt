package com.sameerasw.airsync.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MirroringOptions(
    val fps: Int = 30,
    val quality: Float = 0.8f,
    val maxWidth: Int = 1280,
    val bitrateKbps: Int = 12000
) : Parcelable
