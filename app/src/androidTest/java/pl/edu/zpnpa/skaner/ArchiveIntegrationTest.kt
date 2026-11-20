package pl.edu.zpnpa.skaner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import pl.edu.zpnpa.skaner.data.*
import pl.edu.zpnpa.skaner.security.ArchiveKeys
import java.io.File
import java.util.UUID
import java.security.SecureRandom

class ArchiveIntegrationTest {
    /** A separate app sandbox prevents the test from changing an existing personal archive. */
    private class IsolatedContext(private val original: Context, private val root: File) : android.content.ContextWrapper(original) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir() = File(root, "files").apply { mkdirs() }
        override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }
        override fun getDatabasePath(name: String) = if (File(name).isAbsolute) File(name) else File(root, name)
    }

    @Test fun scanOcrEncryptedPersistenceSearchExportAndDelete() = runBlocking {
        val original = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(original.cacheDir, "test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = IsolatedContext(original, root)
        val keys = ArchiveKeys(context)
        val wrappedKey = keys.databasePassphrase()
        assertArrayEquals(wrappedKey, ArchiveKeys(context).databasePassphrase())
        assertFalse(File(context.noBackupFilesDir, "database.key").readBytes().contentEquals(wrappedKey))
        System.loadLibrary("sqlcipher")
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        fun openDatabase() = Room.databaseBuilder(context, ArchiveDatabase::class.java,
            context.getDatabasePath("test.db").absolutePath)
            .openHelperFactory(SupportOpenHelperFactory(passphrase.copyOf())).build()
        var database = openDatabase()
        val repository = DocumentRepository(context, { database }, { keys.cipher })
        try {
            val photo = createPhoto(root)
            val imported = repository.importScans(listOf(Uri.fromFile(photo), Uri.fromFile(photo))) { }
            assertFalse("Bundled OCR should recognize the printed fixture", imported.ocrFailed)
            val initial = repository.details(imported.id)
            assertEquals(2, initial.pages.size)
            assertTrue(initial.document.text.lowercase().contains("internet"))
            repository.update(imported.id, "Łączność", initial.document.text)
            assertEquals(1, repository.observe("LACZNOSC").first().size)
            assertEquals(1, repository.observe("internet").first().size)
            val scan = File(context.filesDir, "archive/${initial.pages.first().fileName}")
            assertFalse(scan.readBytes().take(3) == listOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
            val pdfFile = File(root, "output.pdf")
            repository.exportPdf(imported.id, Uri.fromFile(pdfFile)) { }
            assertEquals("%PDF", pdfFile.readBytes().take(4).toByteArray().toString(Charsets.US_ASCII))
            PdfRenderer(ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)).use {
                assertEquals(2, it.pageCount)
            }
            database.close()
            val rawDatabase = context.getDatabasePath("test.db").readBytes()
            assertFalse(rawDatabase.toString(Charsets.ISO_8859_1).contains("SQLite format 3"))
            assertFalse(rawDatabase.toString(Charsets.UTF_8).contains("Łączność"))
            database = openDatabase()
            assertEquals("Łączność", repository.details(imported.id).document.title)
            val restoredBitmap = repository.bitmap(repository.details(imported.id).pages.first())
            assertTrue(restoredBitmap.width > 0)
            restoredBitmap.recycle()
            repository.delete(imported.id)
            assertTrue(repository.observe("").first().isEmpty())
            assertFalse(scan.exists())
        } finally {
            database.close()
            passphrase.fill(0)
            root.deleteRecursively()
        }
    }

    @Test fun sqlSearchTreatsPercentAndUnderscoreLiterally() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, ArchiveDatabase::class.java).build()
        try {
            db.documents().insertDocument(DocumentEntity("1", "VAT", "12%_VAT", "vat 12%_vat", 1, 0))
            db.documents().insertDocument(DocumentEntity("2", "Other", "12xxVAT", "other 12xxvat", 2, 0))
            assertEquals(listOf("1"), db.documents().observeDocuments(pl.edu.zpnpa.skaner.domain.SearchText.likeQuery("%_")).first().map { it.id })
        } finally { db.close() }
    }

    private fun createPhoto(root: File): File {
        val bitmap = Bitmap.createBitmap(1000, 1400, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 48f }
            drawText("Rachunek za internet", 70f, 170f, paint)
            paint.textSize = 34f
            drawText("Numer: FV/11/2026", 70f, 290f, paint)
            drawText("Kwota: 129,00 PLN", 70f, 370f, paint)
            drawText("Termin: 30.11.2026", 70f, 450f, paint)
        }
        return File(root, "fixture.jpg").also { target ->
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            bitmap.recycle()
        }
    }
}
