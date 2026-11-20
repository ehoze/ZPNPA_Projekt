package pl.edu.zpnpa.skaner

import org.junit.Assert.*
import org.junit.Test
import pl.edu.zpnpa.skaner.security.ArchiveCipher
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator

class ArchiveCipherTest {
    private fun cipher() = ArchiveCipher(KeyGenerator.getInstance("AES").apply { init(256) }.generateKey())

    @Test fun polishTextSurvivesEncryption() {
        val cipher = cipher()
        val original = "Zażółć gęślą jaźń. Faktura 129,00 zł".toByteArray()
        assertArrayEquals(original, cipher.decrypt(cipher.encrypt(original)))
    }
    @Test fun repeatedEncryptionUsesDifferentIvs() {
        val cipher = cipher()
        assertFalse(cipher.encrypt("same".toByteArray()).contentEquals(cipher.encrypt("same".toByteArray())))
    }
    @Test(expected = AEADBadTagException::class) fun modifiedCiphertextIsRejected() {
        val cipher = cipher()
        val bytes = cipher.encrypt("document".toByteArray())
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        cipher.decrypt(bytes)
    }
    @Test(expected = AEADBadTagException::class) fun differentKeyIsRejected() {
        cipher().decrypt(cipher().encrypt("secret".toByteArray()))
    }
    @Test(expected = IllegalArgumentException::class) fun truncatedFileIsRejected() {
        cipher().decrypt(byteArrayOf(1, 2))
    }
    @Test(expected = IllegalArgumentException::class) fun unknownVersionIsRejected() {
        val cipher = cipher()
        val bytes = cipher.encrypt("secret".toByteArray()).apply { this[0] = 2 }
        cipher.decrypt(bytes)
    }
}
