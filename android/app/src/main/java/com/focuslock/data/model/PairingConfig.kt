package com.focuslock.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Shared cryptographic pairing credentials between Fedora laptop and Android phone.
 */
@Parcelize
data class PairingConfig(
    val isPaired: Boolean = false,
    val hmacSecretHex: String = "",
    val laptopHost: String = "",
    val laptopPort: Int = 5000,
    val localHttpServerPort: Int = 8765,
    val pairedAtTimestamp: Long = 0L,
    val lastNonce: String = ""
) : Parcelable
