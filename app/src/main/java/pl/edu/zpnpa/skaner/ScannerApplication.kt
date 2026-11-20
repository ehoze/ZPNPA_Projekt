package pl.edu.zpnpa.skaner

import android.app.Application
import android.content.Context
import androidx.room.Room
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import pl.edu.zpnpa.skaner.data.ArchiveDatabase
import pl.edu.zpnpa.skaner.data.DocumentRepository
import pl.edu.zpnpa.skaner.data.SettingsRepository
import pl.edu.zpnpa.skaner.security.ArchiveKeys

class ScannerApplication : Application() {
    val container by lazy { AppContainer(this) }
}

/** Small explicit dependency container: no DI framework is needed for four screens. */
class AppContainer(context: Context) {
    private val keys = ArchiveKeys(context)
    private val database: ArchiveDatabase by lazy {
        System.loadLibrary("sqlcipher")
        val passphrase = keys.databasePassphrase()
        try {
            Room.databaseBuilder(context, ArchiveDatabase::class.java, "archive.db")
                .openHelperFactory(SupportOpenHelperFactory(passphrase.copyOf()))
                .build()
        } finally {
            passphrase.fill(0)
        }
    }
    val documents = DocumentRepository(context, { database }, { keys.cipher })
    val settings = SettingsRepository(context)
}
