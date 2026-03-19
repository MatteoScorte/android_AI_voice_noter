package com.transcriber.app.data

import com.google.gson.annotations.SerializedName

data class InboxItem(
    @SerializedName("id") val id: String,
    @SerializedName("displayName") val displayName: String,
    @SerializedName("localPath") val localPath: String,
    @SerializedName("addedAt") val addedAt: Long,
    @SerializedName("sizeBytes") val sizeBytes: Long = 0
)
