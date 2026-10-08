package com.elsco.mindwaymaths.domain.repository

import android.app.Activity
import com.elsco.mindwaymaths.domain.model.*
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val state: Flow<AuthState>
    val uid: String?
    suspend fun signIn(activity: Activity)
    suspend fun signOut()
    suspend fun updateProfile(exam: String, language: String, goal: Int)
    suspend fun deleteAccount(activity: Activity)
}
interface LearningRepository {
    fun catalog(collection: String): Flow<List<CatalogItem>>
    fun videos(): Flow<List<VideoLesson>>
    fun pdfs(): Flow<List<PdfMaterial>>
    suspend fun refreshCatalog(collection: String, nextPage: Boolean = false): Boolean
    suspend fun refreshQuestions(filter: PracticeFilter, nextPage: Boolean = false): Boolean
    suspend fun createSession(filter: PracticeFilter, config: TestConfig? = null): StudySession
    fun session(id: String): Flow<StudySession?>
    suspend fun saveSession(session: StudySession)
    suspend fun submitAnswer(session: StudySession): StudySession
    suspend fun finishTest(session: StudySession): TestResult
    fun history(): Flow<List<TestResult>>
    fun progress(): Flow<ProgressSummary>
    fun bookmarkIds(): Flow<Set<String>>
    suspend fun toggleBookmark(questionId: String)
    suspend fun sync()
    suspend fun clearUser(uid: String)
    suspend fun latestSession(): StudySession?
}
