package com.elsco.mindwaymaths

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elsco.mindwaymaths.core.MindwayTheme
import com.elsco.mindwaymaths.domain.model.*
import com.elsco.mindwaymaths.domain.usecase.LearningEngine
import com.elsco.mindwaymaths.feature.auth.LoginScreen
import com.elsco.mindwaymaths.feature.practice.AnswerExplanationScreen
import com.elsco.mindwaymaths.feature.practice.TestResultScreen
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun loginDisablesSignInUntilConfigurationExists() {
        compose.setContent { MindwayTheme { LoginScreen(false, false, {}, {}) } }
        compose.onNodeWithText("Continue with Google").performScrollTo().assertIsNotEnabled()
    }
    @Test fun configuredLoginInvokesCredentialFlow() {
        var clicked = false
        compose.setContent { MindwayTheme { LoginScreen(true, false, { clicked = true }, {}) } }
        compose.onNodeWithText("Continue with Google").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(clicked) }
    }
    @Test fun practiceExplanationHasTextualFeedbackAndShortcut() {
        val q = Question(id = "q", explanation = "Divide by four.", shortcut = "25% = 1/4")
        compose.setContent { MindwayTheme { AnswerExplanationScreen(q, true, 3000) } }
        compose.onNodeWithText("Correct · Nicely done").assertIsDisplayed()
        compose.onNodeWithText("25% = 1/4").assertIsDisplayed()
    }
    @Test fun testResultExposesReviewAction() {
        var reviewed = false
        val q = Question(id = "q", options = listOf("1", "2", "3", "4"))
        val result = LearningEngine().score(StudySession("s", "u", listOf(q), TestConfig(count = 1), startedAt = 0), 1000)
        compose.setContent { MindwayTheme { TestResultScreen(result, { reviewed = true }, {}) } }
        compose.onNodeWithText("Review answers and explanations").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(reviewed) }
    }
}
@RunWith(AndroidJUnit4::class)
class SecureWindowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun protectedWindowIsSecureBeforeLogin() {
        compose.runOnIdle { assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) }
    }
}
