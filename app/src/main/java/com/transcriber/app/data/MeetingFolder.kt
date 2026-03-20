package com.transcriber.app.data

import com.google.gson.annotations.SerializedName
import java.util.UUID

data class MeetingFolder(
    @SerializedName("id")       val id: String       = UUID.randomUUID().toString(),
    @SerializedName("name")     val name: String,
    @SerializedName("colorHex") val colorHex: String = "#4CAF50"
)

/** Special sentinel used as selectedFolderId to mean "show only unassigned meetings". */
const val FOLDER_ID_NONE = "__none__"

/** Preset colors offered when creating a new folder. */
val FOLDER_PRESET_COLORS = listOf(
    "#4CAF50", // verde
    "#2196F3", // blu
    "#FF9800", // arancione
    "#E91E63", // rosa
    "#9C27B0", // viola
    "#00BCD4"  // ciano
)
