package com.elsco.mindwaymaths

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.elsco.mindwaymaths.core.ConnectivityMonitor
import com.elsco.mindwaymaths.core.MindwayTheme
import com.elsco.mindwaymaths.data.local.*
import com.elsco.mindwaymaths.data.remote.*
import com.elsco.mindwaymaths.data.repository.OfflineLearningRepository
import com.elsco.mindwaymaths.domain.model.*
import com.elsco.mindwaymaths.domain.usecase.LearningEngine
import com.elsco.mindwaymaths.feature.AppViewModel
import com.elsco.mindwaymaths.feature.pdfs.*
import com.elsco.mindwaymaths.feature.practice.*
import com.elsco.mindwaymaths.navigation.MindwayApp
import com.elsco.mindwaymaths.network.NetworkClient
import com.elsco.mindwaymaths.security.PrivatePdfCache
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.*

class NavigationFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var db: MindwayDatabase
    private lateinit var repository: OfflineLearningRepository
    private lateinit var vm: AppViewModel
    private lateinit var cache: PrivatePdfCache
    private val auth = TestAuth()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    @Before fun prepare() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, MindwayDatabase::class.java).build()
        val firebase = FirebaseProvider()
        val preferences = PreferencesStore(context)
        preferences.update(Preferences(exam = "ssc"))
        repository = OfflineLearningRepository(db, db.learningDao(), FirestoreContentSource(firebase), firebase, auth, json, LearningEngine())
        cache = PrivatePdfCache(context, NetworkClient(firebase), auth, preferences)
        val q = Question(id = "q1", questionText = "What is 25% of 240?", options = listOf("40", "50", "60", "80"), correctOption = 2, explanation = "Divide 240 by four to get 60.")
        db.learningDao().questions(listOf(QuestionEntity(q.id, q.exam, q.subject, q.topic, q.difficulty, q.type.name, null, json.encodeToString(q))))
        db.learningDao().catalog(listOf(CatalogEntity("exams", "ssc", 1, json.encodeToString(CatalogItem("ssc", "SSC")))))
        withContext(Dispatchers.Main) { vm = AppViewModel(auth, repository, preferences, ConnectivityMonitor(context), cache, firebase, context) }
    }
    @After fun close() { db.close() }
    @Test fun bottomNavigationReachesPracticeTestsAndProfile() {
        compose.setContent { MindwayApp(vm) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Ready to improve your score?").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nav-PracticeSetup").performClick()
        compose.onNodeWithText("Practice with purpose.").assertIsDisplayed()
        compose.onNodeWithTag("nav-TestSetup").performClick()
        compose.onNodeWithText("Make it exam day.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithText("Settings").performScrollTo().assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Delete account"))
        compose.onNodeWithText("Delete account").performClick()
        compose.onNodeWithText("Permanently delete your account?").assertIsDisplayed()
        compose.onNodeWithText("Keep account").performClick()
    }
    @Test fun savedPracticeSupportsFeedbackAndBookmark() {
        val session = runBlocking { repository.createSession(PracticeFilter()) }
        lateinit var sessionVm: SessionViewModel
        compose.runOnUiThread { sessionVm = SessionViewModel(SavedStateHandle(mapOf("id" to session.id)), repository, LearningEngine(), context) }
        compose.setContent { MindwayTheme { QuestionScreen(sessionVm, {}, {}) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("C. 60").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("C. 60").performScrollTo().performClick()
        compose.onNodeWithText("Check answer").performScrollTo().performClick()
        compose.onNodeWithText("Correct · Nicely done").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Bookmark").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Bookmarked").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun pdfInvalidMetadataHasRetryAndNoExportActions() {
        lateinit var pdfVm: PdfViewModel
        compose.runOnUiThread { pdfVm = PdfViewModel(SavedStateHandle(mapOf("id" to "../invalid")), cache) }
        compose.setContent { MindwayTheme { PdfViewerScreen(pdfVm, "Revision notes") } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Search document").assertIsDisplayed()
        compose.onNodeWithText("Share").assertDoesNotExist()
        compose.onNodeWithText("Export").assertDoesNotExist()
    }
}
