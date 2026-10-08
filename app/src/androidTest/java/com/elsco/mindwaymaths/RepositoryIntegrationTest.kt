package com.elsco.mindwaymaths

import android.app.Activity
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.elsco.mindwaymaths.data.local.*
import com.elsco.mindwaymaths.data.remote.*
import com.elsco.mindwaymaths.data.repository.OfflineLearningRepository
import com.elsco.mindwaymaths.domain.model.*
import com.elsco.mindwaymaths.domain.repository.AuthRepository
import com.elsco.mindwaymaths.domain.usecase.LearningEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*

class TestAuth : AuthRepository {
    val mutable = MutableStateFlow<AuthState>(AuthState.SignedIn(UserProfile(uid = "student", selectedExam = "ssc")))
    override val state: Flow<AuthState> = mutable
    override val uid get() = (mutable.value as? AuthState.SignedIn)?.user?.uid
    override suspend fun signIn(activity: Activity) { mutable.value = AuthState.SignedIn(UserProfile(uid = "student", selectedExam = "ssc")) }
    override suspend fun signOut() { mutable.value = AuthState.SignedOut }
    override suspend fun updateProfile(exam: String, language: String, goal: Int) {}
    override suspend fun deleteAccount(activity: Activity) { signOut() }
}
class RepositoryIntegrationTest {
    private lateinit var db: MindwayDatabase
    private lateinit var repo: OfflineLearningRepository
    private val auth = TestAuth()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val question = Question(id = "q1", questionText = "2 + 2?", options = listOf("1", "2", "3", "4"), correctOption = 3)
    @Before fun setup() = kotlinx.coroutines.runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MindwayDatabase::class.java).build()
        val firebase = FirebaseProvider()
        repo = OfflineLearningRepository(db, db.learningDao(), FirestoreContentSource(firebase), firebase, auth, json, LearningEngine())
        db.learningDao().questions(listOf(QuestionEntity(question.id, "ssc", "aptitude", "percentage", "Medium", "MCQ", null, json.encodeToString(question))))
    }
    @After fun close() { db.close() }
    @Test fun offlineAnswersAreDurableAndIdempotent() = runTest {
        val session = repo.createSession(PracticeFilter())
        val answered = session.copy(answers = mapOf("q1" to Answer(option = 3, timeMillis = 3000)))
        repo.submitAnswer(answered); repo.submitAnswer(answered)
        assertEquals(1, repo.progress().first().solved)
        assertEquals(1, repo.progress().first().correct)
        assertTrue("q1" in repo.session(session.id).first()!!.submittedQuestions)
    }
    @Test fun userIsolationAndBookmarks() = runTest {
        repo.toggleBookmark("q1")
        assertEquals(setOf("q1"), repo.bookmarkIds().first())
        auth.mutable.value = AuthState.SignedIn(UserProfile(uid = "other"))
        assertTrue(repo.bookmarkIds().first().isEmpty())
        assertEquals(0, repo.progress().first().solved)
    }
    @Test fun timedResultSurvivesRepeatedSubmission() = runTest {
        val s = repo.createSession(PracticeFilter(), TestConfig(count = 1))
        val answered = s.copy(answers = mapOf("q1" to Answer(option = 0, timeMillis = 5000)))
        val first = repo.finishTest(answered); val second = repo.finishTest(answered)
        assertEquals(first, second); assertEquals(-.25, first.score, .001)
        assertEquals(1, repo.history().first().size); assertTrue(repo.session(s.id).first()!!.completed)
    }
    @Test fun emptyFilteredSetFailsWithoutCreatingSession() = runTest {
        try { repo.createSession(PracticeFilter(topic = "nonexistent")); fail("Expected empty-set rejection") }
        catch (_: IllegalArgumentException) { assertNull(repo.latestSession()) }
    }
    @Test fun deletionRemovesAllLocalUserData() = runTest {
        val s = repo.createSession(PracticeFilter())
        repo.submitAnswer(s.copy(answers = mapOf("q1" to Answer(option = 3))))
        repo.toggleBookmark("q1"); repo.clearUser("student")
        assertEquals(0, repo.progress().first().solved); assertTrue(repo.bookmarkIds().first().isEmpty()); assertNull(repo.latestSession())
    }
}
