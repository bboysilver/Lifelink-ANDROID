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
    fun `setup opens the real dashboard`() {
        composeRule.onNodeWithText("라이프링크 시작하기").assertIsDisplayed()
        composeRule.onNodeWithText("설정 시작").performClick()
        composeRule.onNodeWithText("안전 기능 설정을 확인해 주세요").assertIsDisplayed()
    }
}