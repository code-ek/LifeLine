package com.lifeline.app.emergency

enum class EmergencyType(val label: String) {
    MEDICAL("Medical"),
    FIRE("Fire"),
    TRAPPED("Trapped"),
    VIOLENCE("Violence"),
    FLOOD("Flood"),
    OTHER("Other");

    companion object {
        /** Unknown types from newer clients still surface as an SOS rather than being dropped. */
        fun fromWire(value: String): EmergencyType =
            entries.firstOrNull { it.name == value } ?: OTHER
    }
}

data class SosLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null
)

/** What travels over the mesh. */
data class SosPayload(
    val id: String,
    val type: EmergencyType,
    val description: String,
    val timestampMs: Long,
    val location: SosLocation?
)

/** An SOS as seen by this device: the payload plus who sent it and when it arrived. */
data class SosAlert(
    val payload: SosPayload,
    val senderPeerID: String?,
    val senderNickname: String?,
    val receivedAtMs: Long,
    val isLocal: Boolean
)
