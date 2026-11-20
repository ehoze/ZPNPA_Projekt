package pl.edu.zpnpa.skaner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import pl.edu.zpnpa.skaner.data.*
import pl.edu.zpnpa.skaner.ui.*
import java.io.File

/** Reproducible documentation screenshots use fictitious data and the real screen composables. */
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val document = DocumentEntity("demo", "Rachunek za internet",
        "Rachunek za internet\nNumer: FV/11/2026\nKwota: 129,00 PLN\nTermin: 30.11.2026", "", 1_795_161_600_000, 1)

    @Test fun archive() {
        compose.setContent { ScannerTheme {
            ArchiveScreen(listOf(document), "", false, null, {}, {}, {}, {}, {})
        } }
        capture("01-archiwum")
    }
    @Test fun details() {
        compose.setContent { ScannerTheme {
            DetailScreen(DocumentDetails(document, listOf(PageEntity("demo", 0, "fixture"))),
                {}, {}, {}, {}, {}, { photo() })
        } }
        capture("02-dokument")
    }
    @Test fun editor() {
        compose.setContent { ScannerTheme { EditScreen(document, {}, { _, _ -> }) } }
        capture("03-edycja")
    }
    @Test fun settings() {
        compose.setContent { ScannerTheme { SettingsScreen(false, {}, {}) } }
        capture("04-ustawienia")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(root, "$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }

    private fun photo(): Bitmap = Bitmap.createBitmap(800, 1100, Bitmap.Config.ARGB_8888).also { bitmap ->
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 42f }
            drawText("Rachunek za internet", 50f, 130f, paint)
            paint.textSize = 28f
            drawText("Numer: FV/11/2026", 50f, 230f, paint)
            drawText("Kwota: 129,00 PLN", 50f, 310f, paint)
            drawText("Termin: 30.11.2026", 50f, 390f, paint)
        }
    }
}
