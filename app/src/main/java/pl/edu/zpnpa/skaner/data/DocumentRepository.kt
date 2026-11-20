package pl.edu.zpnpa.skaner.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.AtomicFile
import androidx.room.withTransaction
import androidx.core.graphics.scale
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import pl.edu.zpnpa.skaner.domain.DocumentRules
import pl.edu.zpnpa.skaner.domain.SearchText
import pl.edu.zpnpa.skaner.security.ArchiveCipher
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class DocumentDetails(val document: DocumentEntity, val pages: List<PageEntity>)
data class ImportResult(val id: String, val ocrFailed: Boolean)

class DocumentRepository(
    private val context: Context,
    private val database: () -> ArchiveDatabase,
    private val cipher: () -> ArchiveCipher,
) {
    private val archive = File(context.filesDir, "archive")
    private val mutation = Mutex()

    fun observe(query: String): Flow<List<DocumentEntity>> = flow {
        emitAll(database().documents().observeDocuments(SearchText.likeQuery(query)))
    }.flowOn(Dispatchers.IO)

    suspend fun details(id: String): DocumentDetails = withContext(Dispatchers.IO) {
        val dao = database().documents()
        DocumentDetails(checkNotNull(dao.document(id)) { "Dokument nie istnieje." }, dao.pages(id))
    }

    suspend fun importScans(uris: List<Uri>, progress: (String) -> Unit): ImportResult =
        withContext(Dispatchers.IO) {
            mutation.withLock {
                require(uris.isNotEmpty() && uris.size <= DocumentRules.MAX_PAGES) {
                    "Wybierz od 1 do ${DocumentRules.MAX_PAGES} stron."
                }
                val db = database()
                archive.mkdirs()
                val id = UUID.randomUUID().toString()
                val pages = mutableListOf<PageEntity>()
                val texts = mutableListOf<String>()
                var ocrFailed = false
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                try {
                    uris.forEachIndexed { index, uri ->
                        progress("Zapisywanie i OCR: strona ${index + 1} z ${uris.size}")
                        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                            val buffer = ByteArray(8192)
                            val output = ByteArrayOutputStream()
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                require(output.size() + count <= MAX_INPUT_BYTES) { "Zdjęcie jest zbyt duże (maks. 30 MB)." }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        } ?: error("Nie można otworzyć zdjęcia.")
                        val bitmap = decode(bytes, 2400)
                        try {
                            val name = "$id-$index.scan"
                            writeEncrypted(name, bitmap.jpegBytes())
                            pages += PageEntity(id, index, name)
                            val text = try {
                                recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                ocrFailed = true
                                ""
                            }
                            texts += text
                        } finally { bitmap.recycle() }
                    }
                    val text = texts.joinToString("\n\n").take(DocumentRules.MAX_TEXT)
                    val title = "Dokument " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.forLanguageTag("pl")).format(Date())
                    val document = DocumentEntity(id, title, text, SearchText.normalize("$title\n$text"),
                        System.currentTimeMillis(), pages.size)
                    db.withTransaction {
                        db.documents().insertDocument(document)
                        db.documents().insertPages(pages)
                    }
                    ImportResult(id, ocrFailed)
                } catch (error: Exception) {
                    pages.forEach { File(archive, it.fileName).delete() }
                    throw error
                } finally { recognizer.close() }
            }
        }

    suspend fun update(id: String, title: String, text: String) = withContext(Dispatchers.IO) {
        require(DocumentRules.titleError(title) == null) { DocumentRules.titleError(title).orEmpty() }
        require(DocumentRules.textError(text) == null) { DocumentRules.textError(text).orEmpty() }
        mutation.withLock {
            val dao = database().documents()
            val original = checkNotNull(dao.document(id)) { "Dokument nie istnieje." }
            dao.update(original.copy(title = title.trim(), text = text,
                searchText = SearchText.normalize("${title.trim()}\n$text")))
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutation.withLock {
            val db = database()
            val pages = db.documents().pages(id)
            db.withTransaction { db.documents().delete(id) }
            pages.forEach { File(archive, it.fileName).delete() }
        }
    }

    suspend fun bitmap(page: PageEntity, maxDimension: Int = 1600): Bitmap = withContext(Dispatchers.IO) {
        val file = safeFile(page.fileName)
        decode(cipher().decrypt(AtomicFile(file).readFully()), maxDimension)
    }

    suspend fun retryOcr(id: String, progress: (String) -> Unit) = withContext(Dispatchers.IO) {
        val details = details(id)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val texts = details.pages.mapIndexed { index, page ->
                progress("Rozpoznawanie tekstu: strona ${index + 1} z ${details.pages.size}")
                val image = bitmap(page, 2400)
                try { recognizer.process(InputImage.fromBitmap(image, 0)).await().text }
                finally { image.recycle() }
            }
            update(id, details.document.title, texts.joinToString("\n\n").take(DocumentRules.MAX_TEXT))
        } finally { recognizer.close() }
    }

    suspend fun exportPdf(id: String, target: Uri, progress: (String) -> Unit) = withContext(Dispatchers.IO) {
        val details = details(id)
        val pdf = PdfDocument()
        try {
            details.pages.forEachIndexed { index, page ->
                progress("Tworzenie PDF: strona ${index + 1} z ${details.pages.size}")
                val bitmap = bitmap(page, 2400)
                try {
                    val landscape = bitmap.width > bitmap.height
                    val width = if (landscape) 842 else 595
                    val height = if (landscape) 595 else 842
                    val pdfPage = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, index + 1).create())
                    pdfPage.canvas.drawColor(Color.WHITE)
                    val scale = minOf((width - 40f) / bitmap.width, (height - 40f) / bitmap.height)
                    val left = (width - bitmap.width * scale) / 2
                    val top = (height - bitmap.height * scale) / 2
                    pdfPage.canvas.drawBitmap(bitmap, null,
                        RectF(left, top, left + bitmap.width * scale, top + bitmap.height * scale),
                        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    pdf.finishPage(pdfPage)
                } finally { bitmap.recycle() }
            }
            val output = context.contentResolver.openOutputStream(target, "wt")
                ?: error("Nie można zapisać pliku w wybranym miejscu.")
            output.use { pdf.writeTo(it) }
        } finally { pdf.close() }
    }

    /** Recover files left by a process termination between file writes and the DB transaction. */
    suspend fun cleanOrphans() = withContext(Dispatchers.IO) {
        mutation.withLock {
            val retained = database().documents().allFileNames().toSet()
            archive.listFiles()?.filter { it.name !in retained && it.name.removeSuffix(".bak") !in retained }
                ?.forEach { it.delete() }
        }
    }

    private fun writeEncrypted(name: String, bytes: ByteArray) {
        val file = AtomicFile(safeFile(name))
        val output = file.startWrite()
        try {
            output.write(cipher().encrypt(bytes))
            file.finishWrite(output)
        } catch (error: Exception) { file.failWrite(output); throw error }
    }

    private fun safeFile(name: String): File {
        require(name.matches(Regex("[a-f0-9-]{36}-[0-9]+\\.scan"))) { "Nieprawidłowa nazwa pliku." }
        return File(archive, name)
    }

    private fun Bitmap.jpegBytes(): ByteArray = ByteArrayOutputStream().use {
        check(compress(Bitmap.CompressFormat.JPEG, 90, it)) { "Nie udało się zapisać zdjęcia." }
        it.toByteArray()
    }

    private fun decode(bytes: ByteArray, maximum: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Plik nie jest poprawnym zdjęciem." }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maximum * 2) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("Nie można odczytać zdjęcia.")
        val ratio = maximum.toFloat() / maxOf(decoded.width, decoded.height)
        return if (ratio < 1) {
            decoded.scale((decoded.width * ratio).toInt().coerceAtLeast(1),
                (decoded.height * ratio).toInt().coerceAtLeast(1)).also { decoded.recycle() }
        } else decoded
    }

    private companion object { const val MAX_INPUT_BYTES = 30 * 1024 * 1024 }
}
