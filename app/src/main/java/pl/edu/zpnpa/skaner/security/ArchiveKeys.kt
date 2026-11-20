package pl.edu.zpnpa.skaner.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class ArchiveKeys(private val context: Context) {
    val cipher: ArchiveCipher by lazy {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = (store.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build())
            }.generateKey()
        ArchiveCipher(key)
    }

    @Synchronized
    fun databasePassphrase(): ByteArray {
        val file = AtomicFile(File(context.noBackupFilesDir, "database.key"))
        if (file.baseFile.exists()) return cipher.decrypt(file.readFully())
        // Never silently replace a missing key when an existing database needs it.
        check(!context.getDatabasePath("archive.db").exists()) {
            "Brakuje klucza do istniejącego archiwum. Dane nie zostały zmienione."
        }
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val output = file.startWrite()
        try {
            output.write(cipher.encrypt(passphrase))
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
        return passphrase
    }

    private companion object { const val ALIAS = "skaner.archive.v1" }
}
