package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivityUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `setup explains device compatibility before safety features`() {
        composeRule.onNodeWithText("기기 호환성 확인").assertIsDisplayed()
        composeRule.onNodeWithText("기기 상태 다시 확인").performClick()
        composeRule.onNodeWithText("기기 호환성 확인").assertIsDisplayed()
    }
}