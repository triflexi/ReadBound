package app.readbound.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.readbound.MainActivity
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Guards the owners supplied by ComponentActivity while the in-app locale is overridden. */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun libraryStartsWithoutLosingActivityOwners() {
        compose.waitForIdle()
        assertFalse(compose.activity.isFinishing)
        assertFalse(compose.onRoot().printToString().isBlank())
    }
}
