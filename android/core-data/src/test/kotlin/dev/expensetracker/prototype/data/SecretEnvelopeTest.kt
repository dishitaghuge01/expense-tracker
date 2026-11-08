package dev.expensetracker.prototype.data

import java.security.MessageDigest
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class SecretEnvelopeTest {
    private fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    private val secret = ByteArray(32) { (it + 1).toByte() }

    @Test fun `secret round trips without mutating caller bytes`() {
        val key = key()
        val before = secret.copyOf()
        val envelope = SecretEnvelope.seal(key, secret, digest("synthetic-subject"))
        assertArrayEquals(secret, SecretEnvelope.open(key, envelope, digest("synthetic-subject")))
        assertArrayEquals(before, secret)
        assertFalse(envelope.contentEquals(secret))
    }

    @Test fun `fresh random IV gives different envelopes for same subject and secret`() {
        val key = key()
        val subject = digest("synthetic-subject")
        assertFalse(SecretEnvelope.seal(key, secret, subject).contentEquals(SecretEnvelope.seal(key, secret, subject)))
    }

    @Test fun `wrong wrapping key cannot decrypt`() {
        val subject = digest("synthetic-subject")
        val envelope = SecretEnvelope.seal(key(), secret, subject)
        assertThrows(AEADBadTagException::class.java) { SecretEnvelope.open(key(), envelope, subject) }
    }

    @Test fun `swapped subject envelope is rejected`() {
        val key = key()
        val envelope = SecretEnvelope.seal(key, secret, digest("synthetic-subject-a"))
        assertThrows(AEADBadTagException::class.java) { SecretEnvelope.open(key, envelope, digest("synthetic-subject-b")) }
    }

    @Test fun `tampering IV ciphertext or tag is rejected`() {
        val key = key()
        val subject = digest("synthetic-subject")
        val envelope = SecretEnvelope.seal(key, secret, subject)
        for (index in listOf(1, 13, envelope.lastIndex)) {
            val tampered = envelope.copyOf()
            tampered[index] = (tampered[index].toInt() xor 1).toByte()
            assertThrows(AEADBadTagException::class.java) { SecretEnvelope.open(key, tampered, subject) }
        }
    }

    @Test fun `malformed sizes and unknown versions never fall back to plaintext`() {
        val key = key()
        val subject = digest("synthetic-subject")
        val envelope = SecretEnvelope.seal(key, secret, subject)
        for (invalid in listOf(ByteArray(0), secret, envelope.copyOf(envelope.size - 1), envelope + byteArrayOf(0), envelope.copyOf().also { it[0] = 2 })) {
            assertThrows(IllegalArgumentException::class.java) { SecretEnvelope.open(key, invalid, subject) }
        }
        assertThrows(IllegalArgumentException::class.java) { SecretEnvelope.seal(key, ByteArray(31), subject) }
    }
}
