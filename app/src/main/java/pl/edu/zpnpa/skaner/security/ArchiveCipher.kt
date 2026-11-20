package pl.edu.zpnpa.skaner.security

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Versioned authenticated encryption. A fresh, provider-generated IV is used for each file. */
class ArchiveCipher(private val key: SecretKey) {
    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(byteArrayOf(VERSION))
        return byteArrayOf(VERSION) + cipher.iv + cipher.doFinal(plain)
    }

    fun decrypt(encrypted: ByteArray): ByteArray {
        require(encrypted.size >= 1 + IV_LENGTH + 16) { "Nieprawidłowy plik archiwum." }
        require(encrypted[0] == VERSION) { "Nieobsługiwana wersja pliku archiwum." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, encrypted.copyOfRange(1, 13)))
        cipher.updateAAD(byteArrayOf(VERSION))
        return cipher.doFinal(encrypted, 13, encrypted.size - 13)
    }

    private companion object {
        const val IV_LENGTH = 12
        const val VERSION: Byte = 1
    }
}
