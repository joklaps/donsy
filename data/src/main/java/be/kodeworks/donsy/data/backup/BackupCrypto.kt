// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.data.backup

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase protection for exports: a 256-bit key from PBKDF2-HMAC-SHA256 with a random salt,
 * then AES-GCM, which also detects a wrong passphrase or a damaged file. Everything needed to
 * decrypt, except the passphrase, is stored in the file.
 */
internal object BackupCrypto {

    const val KDF = "PBKDF2WithHmacSHA256"

    /** OWASP's 2023 advice for PBKDF2-HMAC-SHA256; about a second on a mid-range phone. */
    const val ITERATIONS = 600_000

    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    private val random = SecureRandom()

    fun encrypt(plain: ByteArray, passphrase: CharArray, iterations: Int = ITERATIONS): EncryptedBackupFile {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        val encoder = Base64.getEncoder()
        return EncryptedBackupFile(
            kdf = KDF,
            iterations = iterations,
            salt = encoder.encodeToString(salt),
            iv = encoder.encodeToString(iv),
            data = encoder.encodeToString(cipher.doFinal(plain)),
        )
    }

    /** The plain file, or null when the passphrase is wrong (or the file was changed). */
    fun decrypt(file: EncryptedBackupFile, passphrase: CharArray): ByteArray? {
        require(file.kdf == KDF) { "Unknown key derivation ${file.kdf}" }
        val decoder = Base64.getDecoder()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(passphrase, decoder.decode(file.salt), file.iterations),
            GCMParameterSpec(TAG_BITS, decoder.decode(file.iv)),
        )
        return try {
            cipher.doFinal(decoder.decode(file.data))
        } catch (_: AEADBadTagException) {
            null
        }
    }

    private fun key(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
