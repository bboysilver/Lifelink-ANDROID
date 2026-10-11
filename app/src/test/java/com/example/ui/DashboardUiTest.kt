package com.example.ui

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.monitoring.SmsSetupIssue
import com.example.monitoring.SmsSetupState
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DashboardUiTest {
    @get:Rule val composeRule = createComposeRule()
    private val viewModelStore = ViewModelStore()
    private lateinit var viewModel: LifeLinkViewModel

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("lifelink_monitoring", Context.MODE_PRIVATE).edit().clear().commit()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        viewModel = LifeLinkViewModel(context)
        viewModelStore.put("dashboard-test", viewModel)
    }

    @After fun tearDown() { viewModelStore.clear() }

    @Test fun homeKeepsSafetyControlsAndHasNoSubscriptionTeaser() {
        composeRule.setContent {
            MaterialTheme {
                DashboardTab(viewModel, SafetyPermissions(false, false, false, false),
                    SmsSetupState.Blocked(SmsSetupIssue.NO_ACTIVE_SIM), {}, {}, {}, {}, {})
            }
        }
        composeRule.onNodeWithText("라이프링크", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_list").performScrollToNode(hasText("이 앱은 의료기기가", substring = true))
        composeRule.onNodeWithText("Life Link Family", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("무료 체험 예정", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText(LifeLinkFamilyPlan.monthlyPriceLabel, substring = true).assertDoesNotExist()
    }
}
