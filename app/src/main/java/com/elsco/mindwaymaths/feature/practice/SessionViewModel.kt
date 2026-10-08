package com.elsco.mindwaymaths.feature.practice

import android.content.Context
import androidx.lifecycle.*
import androidx.navigation.toRoute
import com.elsco.mindwaymaths.domain.model.*
import com.elsco.mindwaymaths.domain.repository.LearningRepository
import com.elsco.mindwaymaths.domain.usecase.LearningEngine
import com.elsco.mindwaymaths.navigation.SessionRoute
import com.elsco.mindwaymaths.worker.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel class SessionViewModel @Inject constructor(
    savedState: SavedStateHandle, private val repository: LearningRepository, val engine: LearningEngine,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val id = savedState.toRoute<SessionRoute>().id
    val session = MutableStateFlow<StudySession?>(null)
    val result = MutableStateFlow<TestResult?>(null)
    val error = MutableStateFlow<String?>(null)
    val remaining = MutableStateFlow(0L)
    val busy = MutableStateFlow(false)
    val bookmarks = repository.bookmarkIds().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    private val mutex = Mutex()
    init {
        viewModelScope.launch {
            repository.session(id).collect { stored ->
                session.value = stored
                if (stored?.completed == true && stored.config != null) result.value = repository.history().first().find { it.sessionId == id }
            }
        }
        viewModelScope.launch { while (isActive) {
            val current = session.value
            remaining.value = current?.deadline?.let { (it - System.currentTimeMillis()).coerceAtLeast(0) } ?: 0
            if (current?.deadline != null && !current.completed && remaining.value == 0L) finish()
            delay(1_000)
        } }
    }
    private fun mutate(block: suspend (StudySession) -> Unit) { viewModelScope.launch { mutex.withLock {
        val current = repository.session(id).first() ?: return@withLock
        busy.value = true
        try { block(current) } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error.value = "Couldn’t save this answer. Please try again." }
        finally { busy.value = false }
    } } }
    fun answer(option: Int? = null, numerical: String? = null) = mutate { current ->
        val question = current.questions[current.index]
        if (current.completed || question.id in current.submittedQuestions || current.deadline?.let { it <= System.currentTimeMillis() } == true) return@mutate
        val previous = current.answers[question.id]
        val elapsed = (System.currentTimeMillis() - current.questionStartedAt).coerceAtLeast(0)
        val answer = Answer(option, numerical, (previous?.timeMillis ?: 0) + elapsed)
        val updated = current.copy(answers = current.answers + (question.id to answer), questionStartedAt = System.currentTimeMillis())
        session.value = updated; repository.saveSession(updated)
    }
    fun move(index: Int) = mutate { current ->
        if (index !in current.questions.indices) return@mutate
        val qid = current.questions[current.index].id
        val answers = current.answers[qid]?.let { answer -> current.answers + (qid to answer.copy(
            timeMillis = answer.timeMillis + (System.currentTimeMillis() - current.questionStartedAt).coerceAtLeast(0))) } ?: current.answers
        val updated = current.copy(index = index, answers = answers, questionStartedAt = System.currentTimeMillis())
        session.value = updated; repository.saveSession(updated)
    }
    fun submitAnswer() = mutate { current ->
        val qid = current.questions[current.index].id
        val answer = current.answers[qid] ?: return@mutate
        val timed = current.copy(answers = current.answers + (qid to answer.copy(timeMillis = answer.timeMillis +
            (System.currentTimeMillis() - current.questionStartedAt).coerceAtLeast(0))), questionStartedAt = System.currentTimeMillis())
        val updated = repository.submitAnswer(timed)
        session.value = updated; WorkScheduler.sync(context)
    }
    fun bookmark() = mutate { repository.toggleBookmark(it.questions[it.index].id); WorkScheduler.sync(context) }
    fun review() = mutate { current ->
        val id = current.questions[current.index].id
        val updated = current.copy(review = if (id in current.review) current.review - id else current.review + id)
        session.value = updated; repository.saveSession(updated)
    }
    fun finish() = mutate { current ->
        if (current.completed) return@mutate
        if (current.config != null) result.value = repository.finishTest(current)
        else repository.saveSession(current.copy(completed = true))
        session.value = current.copy(completed = true)
        WorkScheduler.sync(context)
    }
}
