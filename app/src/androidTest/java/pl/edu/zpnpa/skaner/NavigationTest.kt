package pl.edu.zpnpa.skaner

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun querySurvivesSettingsNavigationAndActivityRecreation() {
        compose.onNodeWithText("Szukaj w nazwie i tekście").performTextReplacement("umowa")
        compose.onNodeWithContentDescription("Ustawienia").performClick()
        compose.onNodeWithText("Prywatność").assertIsDisplayed()
        compose.onNodeWithContentDescription("Wstecz").performClick()
        compose.onNodeWithText("umowa").assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("umowa").assertExists()
    }
}
