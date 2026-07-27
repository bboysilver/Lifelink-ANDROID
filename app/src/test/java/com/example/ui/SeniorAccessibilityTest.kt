package com.example.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp")
class SeniorAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun startupActionRemainsVisibleAndOperableAtTwoHundredPercentText() {
        var completed = false
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                MaterialTheme {
                    StartupSetupDialog(onComplete = { completed = true })
                }
            }
        }

        composeRule.onNodeWithText("설정 시작").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(completed) }
    }
}
