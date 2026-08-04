package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "otp_accounts")
data class OtpAccount(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val accountName: String,
    val secretKey: String,
    val issuer: String,
    val algo: String = "SHA1",
    val digits: Int = 6,
    val period: Int = 30,
    val customLabelColorIndex: Int = 0,
    val isFavorite: Boolean = false,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val type: String = "TOTP", // "TOTP" or "HOTP"
    val counter: Long = 0L // HOTP Counter value
)
