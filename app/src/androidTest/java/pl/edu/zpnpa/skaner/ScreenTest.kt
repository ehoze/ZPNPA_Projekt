package pl.edu.zpnpa.skaner

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import pl.edu.zpnpa.skaner.data.DocumentEntity
import pl.edu.zpnpa.skaner.ui.*

class ScreenTest {
    @get:Rule val compose = createComposeRule()
    private val document = DocumentEntity("test", "Rachunek", "Tekst rachunku", "", 0, 1)

    @Test fun emptyArchiveStartsScanningAndOpensSettings() {
        var scans = 0
        var settings = 0
        compose.setContent { ScannerTheme {
            ArchiveScreen(emptyList(), "", false, null, {}, { scans++ }, { settings++ }, {}, {})
        } }
        compose.onNodeWithText("Zacznij od pierwszego skanu").assertIsDisplayed()
        compose.onNodeWithText("Skanuj dokument").performClick()
        compose.onNodeWithContentDescription("Ustawienia").performClick()
        assertEquals(1, scans)
        assertEquals(1, settings)
    }

    @Test fun editorRejectsBlankTitleAndThenSavesCorrectedData() {
        var saved: Pair<String, String>? = null
        compose.setContent { ScannerTheme { EditScreen(document, {}) { title, text -> saved = title to text } } }
        compose.onNodeWithText("Nazwa dokumentu").performTextReplacement(" ")
        compose.onNodeWithText("Zapisz zmiany").performScrollTo().performClick()
        compose.onNodeWithText("Wpisz nazwę dokumentu.").assertExists()
        assertNull(saved)
        compose.onNodeWithText("Nazwa dokumentu").performTextReplacement("Internet")
        compose.onNodeWithText("Zapisz zmiany").performScrollTo().performClick()
        assertEquals("Internet", saved?.first)
    }

    @Test fun editorRestoresUnsavedTitleAfterRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { ScannerTheme { EditScreen(document, {}, { _, _ -> }) } }
        compose.onNodeWithText("Nazwa dokumentu").performTextReplacement("Nowa nazwa")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Nowa nazwa").assertExists()
    }

    @Test fun searchHasAnExplicitEmptyResult() {
        compose.setContent { ScannerTheme {
            ArchiveScreen(emptyList(), "nieistniejący", false, null, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithText("Brak wyników").assertIsDisplayed()
    }
}
