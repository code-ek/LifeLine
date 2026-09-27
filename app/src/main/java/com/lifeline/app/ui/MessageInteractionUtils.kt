package com.lifeline.app.ui

import com.lifeline.app.model.LifeLineMessage

internal fun LifeLineMessage.isFromSelf(
    currentUserNickname: String,
    myPeerId: String,
): Boolean =
    senderPeerID == myPeerId ||
        sender == currentUserNickname ||
        sender.startsWith("$currentUserNickname#")
