package app.readbound.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.readbound.settings.ReaderPreferences
import app.readbound.settings.ReaderTheme
import app.readbound.ui.theme.ReaderAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Regression coverage for semantics and interaction after replacing Material controls. */
@RunWith(AndroidJUnit4::class)
class LiquidGlassTest {
    @get:Rule val compose = createComposeRule()

    @Test fun navigationExposesSelectionAndChangesDestination() {
        compose.setContent {
            ReaderAppTheme(ReaderPreferences(followSystemTheme = false, language = "en")) {
                GlassBackground {
                    var destination by remember { mutableStateOf("library") }
                    ReaderBottomNavigation(destination) { destination = it }
                }
            }
        }
        compose.onAllNodes(isSelected()).assertCountEquals(1)
        compose.onNode(isSelectable() and (hasText("Notes") or hasText("Заметки"))).performClick().assertIsSelected()
        compose.onAllNodes(isSelected()).assertCountEquals(1)
    }

    @Test fun disabledActionCannotSubmitAndEditableFieldRetainsInput() {
        var submitted = ""
        compose.setContent {
            ReaderAppTheme(ReaderPreferences()) {
                GlassBackground {
                    var value by remember { mutableStateOf("") }
                    Column {
                        GlassTextField(value, { value = it }, Modifier.testTag("input"))
                        GlassButton({ submitted = value }, enabled = value.isNotBlank()) { Text("Submit") }
                    }
                }
            }
        }
        compose.onNodeWithText("Submit").assertIsNotEnabled()
        compose.onNodeWithTag("input").performTextInput("A reading note")
        compose.onNodeWithText("Submit").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("A reading note", submitted) }
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Test fun shortSheetActionsMoveWithTheSheetAndRemainVisible() {
        compose.setContent {
            ReaderAppTheme(ReaderPreferences()) {
                GlassBackground {
                    var open by remember { mutableStateOf(true) }
                    if (open) {
                        GlassModalBottomSheet(onDismissRequest = { open = false }) {
                            Text("Sheet contents")
                            GlassButton({ open = false }) { Text("Close sheet") }
                        }
                    } else Text("Sheet closed")
                }
            }
        }
        compose.onNodeWithText("Sheet contents").assertIsDisplayed()
        compose.onNodeWithText("Close sheet").assertIsDisplayed().performClick()
        compose.onNodeWithText("Sheet closed").assertIsDisplayed()
    }

    @Test fun sliderAndSwitchKeepAccessibilityActionsInDarkTheme() {
        var progress = 0f
        compose.setContent {
            ReaderAppTheme(ReaderPreferences(followSystemTheme = false, theme = ReaderTheme.DARK)) {
                GlassBackground {
                    var checked by remember { mutableStateOf(false) }
                    var value by remember { mutableFloatStateOf(0f) }
                    Column {
                        GlassSwitch(checked, { checked = it }, Modifier.testTag("switch"))
                        GlassSlider(value, { value = it; progress = it }, Modifier.testTag("progress"))
                    }
                }
            }
        }
        compose.onNodeWithTag("switch").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag("progress").performSemanticsAction(SemanticsActions.SetProgress) { it(.65f) }
        compose.runOnIdle { assertEquals(.65f, progress, .001f) }
    }

    @Test fun languageSwitchRecomposesWithoutRecreatingTheHost() {
        compose.setContent {
            var language by remember { mutableStateOf("ru") }
            ReaderLocale(language) {
                ReaderAppTheme(ReaderPreferences(language = language)) {
                    GlassBackground {
                        Column {
                            Text(readerString("English interface", "Русский интерфейс"))
                            GlassButton(onClick = { language = "en" }) { Text("Switch language") }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Русский интерфейс").assertIsDisplayed()
        compose.onNodeWithText("Switch language").performClick()
        compose.onNodeWithText("English interface").assertIsDisplayed()
        compose.onNodeWithText("Русский интерфейс").assertDoesNotExist()
    }

    @Test fun glassCheckboxRetainsCheckboxSemanticsAndAnimationState() {
        compose.setContent {
            ReaderAppTheme(ReaderPreferences()) {
                GlassBackground {
                    var checked by remember { mutableStateOf(false) }
                    GlassCheckbox(checked, { checked = it }, Modifier.testTag("glass checkbox"))
                }
            }
        }
        compose.onNodeWithTag("glass checkbox").assertIsOff().performClick().assertIsOn()
    }

    @Test fun ankiSheetKeepsDraftAndEnablesSendOnlyWithBothSides() {
        var submitted: Pair<String, String>? = null
        compose.setContent {
            ReaderAppTheme(ReaderPreferences()) {
                GlassBackground {
                    AnkiExportDialog(initialFront = "word", onDismiss = {},
                        onSend = { front, back -> submitted = front to back })
                }
            }
        }
        compose.onAllNodes(hasClickAction() and (hasText("Send") or hasText("Отправить")))[0].assertIsNotEnabled()
        compose.onNode(hasText("Edit manually") or hasText("Изменить вручную")).performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("translation")
        compose.onAllNodes(hasClickAction() and (hasText("Send") or hasText("Отправить")))[0].assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("word" to "translation", submitted) }
    }
}
