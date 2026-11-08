package dev.expensetracker.prototype.data

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Versioned AES-GCM envelope. The subject digest is authenticated, not stored as plaintext identity. */
internal object SecretEnvelope {
    const val SECRET_BYTES = 32
    private const val IV_BYTES = 12
    const val ENVELOPE_BYTES = 1 + IV_BYTES + SECRET_BYTES + 16

    fun seal(key: SecretKey, secret: ByteArray, subjectDigest: ByteArray): ByteArray {
        require(secret.size == SECRET_BYTES) { "INVALID_SECRET_SIZE" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        require(cipher.iv.size == IV_BYTES) { "INVALID_IV_SIZE" }
        cipher.updateAAD(subjectDigest)
        return byteArrayOf(1) + cipher.iv + cipher.doFinal(secret)
    }

    fun open(key: SecretKey, envelope: ByteArray, subjectDigest: ByteArray): ByteArray {
        require(envelope.size == ENVELOPE_BYTES && envelope[0] == 1.toByte()) { "INVALID_KEY_ENVELOPE" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, envelope.copyOfRange(1, 1 + IV_BYTES)))
        cipher.updateAAD(subjectDigest)
        return cipher.doFinal(envelope, 1 + IV_BYTES, envelope.size - 1 - IV_BYTES)
    }
}
