package com.example.util

import android.util.Log
import java.nio.ByteBuffer
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Base32 {
    fun decode(base32: String): ByteArray {
        val normalized = base32.uppercase(Locale.US).replace("-", "").replace(" ", "").trimEnd('=')
        if (normalized.isEmpty()) return ByteArray(0)
        
        val bytes = ByteArray(normalized.length * 5 / 8)
        var buffer = 0
        var bitsLeft = 0
        var count = 0
        
        for (c in normalized) {
            val value = when (c) {
                in 'A'..'Z' -> c - 'A'
                in '2'..'7' -> c - '2' + 26
                else -> {
                    // Ignore invalid characters safely rather than crashing
                    continue
                }
            }
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                if (count < bytes.size) {
                    bytes[count++] = (buffer shr (bitsLeft - 8)).toByte()
                }
                bitsLeft -= 8
            }
        }
        
        // If the result array has unused trailing elements because of filtered non-base32 chars, trim it
        return if (count < bytes.size) bytes.copyOf(count) else bytes
    }
}

object TotpGenerator {
    private const val TAG = "TotpGenerator"

    fun generateTotp(
        secret: String,
        timeMs: Long = System.currentTimeMillis(),
        periodSec: Int = 30,
        digits: Int = 6,
        algorithm: String = "SHA1"
    ): String {
        val counter = timeMs / 1000 / periodSec
        return generateOtpWithCounter(secret, counter, digits, algorithm)
    }

    fun generateOtpWithCounter(
        secret: String,
        counter: Long,
        digits: Int = 6,
        algorithm: String = "SHA1"
    ): String {
        return try {
            val key = Base32.decode(secret)
            if (key.isEmpty()) {
                return "0".repeat(digits)
            }

            val data = ByteBuffer.allocate(8).putLong(counter).array()

            val macAlgorithm = when (algorithm.uppercase(Locale.US)) {
                "SHA256" -> "HmacSHA256"
                "SHA512" -> "HmacSHA512"
                else -> "HmacSHA1"
            }

            val mac = Mac.getInstance(macAlgorithm)
            val keySpec = SecretKeySpec(key, macAlgorithm)
            mac.init(keySpec)
            val hash = mac.doFinal(data)

            val offset = hash[hash.size - 1].toInt() and 0xF
            val binary = (
                ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)
            )

            val pinValue = binary % Math.pow(10.0, digits.toDouble()).toLong()
            String.format(Locale.US, "%0${digits}d", pinValue)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating OTP with counter", e)
            "0".repeat(digits)
        }
    }
}
