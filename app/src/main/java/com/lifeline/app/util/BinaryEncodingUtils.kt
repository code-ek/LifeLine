package com.lifeline.app.util

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

/**
 * Binary encoding utilities for efficient protocol messages
 */

// Hex Encoding/Decoding Extensions

fun ByteArray.hexEncodedString(): String {
    if (this.isEmpty()) {
        return ""
    }
    return this.joinToString("") { "%02x".format(it) }
}

fun String.dataFromHexString(): ByteArray? {
    val len = this.length / 2
    val data = ByteArray(len)
    var index = 0
    
    for (i in 0 until len) {
        val hexByte = this.substring(i * 2, i * 2 + 2)
        val byte = hexByte.toIntOrNull(16)?.toByte() ?: return null
        data[index++] = byte
    }
    
    return data
}

// Binary Encoding Utilities
