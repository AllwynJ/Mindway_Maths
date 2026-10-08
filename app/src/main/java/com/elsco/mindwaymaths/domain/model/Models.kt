package com.elsco.mindwaymaths.domain.model

import kotlinx.serialization.Serializable

@Serializable enum class QuestionType { MCQ, NUMERICAL, TRUE_FALSE }
@Serializable enum class PracticeMode { TOPIC, RANDOM, WEAK, INCORRECT, BOOKMARKS, PREVIOUS_YEAR, DAILY, SPEED, REVISION }
@Serializable data class Question(
    val id: String = "", val exam: String = "ssc", val subject: String = "aptitude",
    val topic: String = "percentage", val subtopic: String = "", val difficulty: String = "Medium",
    val type: QuestionType = QuestionType.MCQ, val questionText: String = "",
    val imageUrl: String? = null, val options: List<String> = emptyList(), val correctOption: Int = 0,
    val numericalAnswer: Double? = null, val tolerance: Double = 0.001,
    val explanation: String = "", val shortcut: String = "", val solution: String = "",
    val source: String = "Mindway Maths", val tags: List<String> = emptyList(), val year: Int? = null,
    val isActive: Boolean = true, val createdAt: Long = 0, val updatedAt: Long = 0,
) {
    fun isValid(): Boolean = id.matches(Regex("[A-Za-z0-9_-]{1,100}")) && questionText.isNotBlank() && questionText.length <= 4000 && explanation.length <= 8000 && options.size <= 8 &&
        if (type == QuestionType.NUMERICAL) numericalAnswer?.isFinite() == true && tolerance >= 0
        else options.size >= (if (type == QuestionType.TRUE_FALSE) 2 else 4) && correctOption in options.indices
}

@Serializable data class Attempt(
    val id: String, val questionId: String, val uid: String, val topic: String,
    val selectedOption: Int? = null, val numericalAnswer: String? = null, val correct: Boolean,
    val timeTaken: Long, val attemptNumber: Int, val timestamp: Long,
)
@Serializable data class Mastery(
    val questionId: String, val topic: String, val score: Int = 0, val attempts: Int = 0,
    val mistakes: Int = 0, val lastSeen: Long = 0, val nextDue: Long = 0, val averageMillis: Long = 0,
)
@Serializable data class PracticeFilter(
    val exam: String = "ssc", val subject: String = "", val topic: String = "",
    val difficulty: String = "", val type: String = "", val mode: PracticeMode = PracticeMode.TOPIC,
)
@Serializable data class TestConfig(
    val count: Int = 10, val durationMinutes: Int = 10, val marks: Double = 1.0,
    val negativeMarks: Double = 0.25, val filter: PracticeFilter = PracticeFilter(),
) {
    init { require(count in 1..100 && durationMinutes in 1..180 && marks > 0 && negativeMarks in 0.0..marks) }
}
@Serializable data class Answer(val option: Int? = null, val numerical: String? = null, val timeMillis: Long = 0)
@Serializable data class StudySession(
    val id: String, val uid: String, val questions: List<Question>, val config: TestConfig? = null,
    val mode: PracticeMode = PracticeMode.TOPIC, val startedAt: Long, val deadline: Long? = null,
    val index: Int = 0, val answers: Map<String, Answer> = emptyMap(),
    val submittedQuestions: Set<String> = emptySet(), val review: Set<String> = emptySet(),
    val completed: Boolean = false, val questionStartedAt: Long = startedAt,
)
@Serializable data class Breakdown(val total: Int, val correct: Int, val attempted: Int)
@Serializable data class TestResult(
    val sessionId: String, val uid: String, val timestamp: Long, val total: Int,
    val attempted: Int, val correct: Int, val incorrect: Int, val skipped: Int,
    val score: Double, val maxScore: Double, val accuracy: Double, val percentage: Double,
    val averageMillis: Long, val topics: Map<String, Breakdown>, val subjects: Map<String, Breakdown>,
)
@Serializable data class UserProfile(
    val uid: String = "", val displayName: String = "", val email: String = "", val photoUrl: String? = null,
    val createdAt: Long = 0, val lastLoginAt: Long = 0, val selectedExam: String = "",
    val selectedLanguage: String = "en", val dailyGoal: Int = 20, val totalQuestionsSolved: Int = 0,
    val totalCorrect: Int = 0, val totalTests: Int = 0, val streak: Int = 0, val appVersion: String = "",
    val deletionPending: Boolean = false,
)
@Serializable data class CatalogItem(
    val id: String = "", val title: String = "", val subject: String = "", val exam: String = "",
    val order: Int = 0, val isActive: Boolean = true,
)
@Serializable data class VideoLesson(
    val id: String = "", val title: String = "", val description: String = "", val youtubeVideoId: String = "",
    val thumbnailUrl: String = "", val topicId: String = "", val subject: String = "", val exam: String = "",
    val duration: Int = 0, val order: Int = 0, val isActive: Boolean = true,
    val visibility: String = "unlisted", val createdAt: Long = 0, val updatedAt: Long = 0,
)
@Serializable data class PdfMaterial(
    val id: String = "", val title: String = "", val description: String = "", val topic: String = "",
    val subject: String = "", val exam: String = "", val pageCount: Int = 0, val fileSize: Long = 0,
    val thumbnailUrl: String = "", val sortOrder: Int = 0, val isActive: Boolean = true,
    val createdAt: Long = 0, val updatedAt: Long = 0,
)
data class ProgressSummary(
    val solved: Int = 0, val correct: Int = 0, val averageMillis: Long = 0,
    val today: Int = 0, val todayCorrect: Int = 0, val streak: Int = 0,
    val incorrect: Int = 0, val bookmarks: Int = 0, val tests: Int = 0,
    val dailyActivity: Map<String, Int> = emptyMap(), val topicMastery: Map<String, Int> = emptyMap(),
) { val accuracy: Double get() = if (solved == 0) 0.0 else correct * 100.0 / solved }
sealed interface AuthState {
    data object Loading : AuthState
    data object Unconfigured : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: UserProfile) : AuthState
}
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data object Empty : LoadState<Nothing>
    data class Success<T>(val data: T, val offline: Boolean = false) : LoadState<T>
    data class Error(val message: String) : LoadState<Nothing>
}
