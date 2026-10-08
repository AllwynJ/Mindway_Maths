package com.elsco.mindwaymaths.domain

import com.elsco.mindwaymaths.domain.model.*
import com.elsco.mindwaymaths.domain.usecase.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LearningEngineTest {
    private val engine = LearningEngine()
    private val question = Question(id = "q1", questionText = "2 + 2?", options = listOf("1", "2", "3", "4"), correctOption = 3)
    @Test fun `MCQ accepts only the correct option`() {
        assertTrue(engine.correct(question, Answer(option = 3)))
        assertFalse(engine.correct(question, Answer(option = 0)))
        assertFalse(engine.correct(question, null))
    }
    @Test fun `numerical answers respect tolerance and reject nonfinite input`() {
        val q = question.copy(type = QuestionType.NUMERICAL, numericalAnswer = 2.5, tolerance = .01)
        assertTrue(engine.correct(q, Answer(numerical = "2.505")))
        assertFalse(engine.correct(q, Answer(numerical = "2.52")))
        assertFalse(engine.correct(q, Answer(numerical = "NaN")))
        assertFalse(engine.answered(Answer(numerical = "Infinity")))
    }
    @Test fun `negative marking and skipped answers are scored separately`() {
        val session = StudySession("s", "u", listOf(question, question.copy(id = "q2"), question.copy(id = "q3")), TestConfig(count = 3),
            startedAt = 1000, answers = mapOf("q1" to Answer(option = 3), "q2" to Answer(option = 0)))
        val result = engine.score(session, 61_000)
        assertEquals(.75, result.score, .001); assertEquals(1, result.skipped)
        assertEquals(50.0, result.accuracy, .001); assertEquals(25.0, result.percentage, .001)
        assertEquals(20_000, result.averageMillis); assertEquals(1, result.topics[question.topic]?.correct)
    }
    @Test fun `all wrong tests may have negative scores`() {
        val s = StudySession("s", "u", listOf(question), TestConfig(count = 1), startedAt = 0, answers = mapOf("q1" to Answer(option = 0)))
        assertEquals(-.25, engine.score(s, 1000).score, .001)
    }
    @Test fun `empty sets have finite progress values`() {
        val r = engine.score(StudySession("s", "u", emptyList(), TestConfig(), startedAt = 0), 1000)
        assertEquals(0.0, r.accuracy, .0); assertEquals(0.0, r.percentage, .0); assertEquals(0L, r.averageMillis)
    }
    @Test fun `mastery is bounded and errors become due immediately`() {
        val mastered = engine.updateMastery(Mastery("q", "t", score = 98), true, 10_000, 100)
        assertEquals(100, mastered.score); assertTrue(mastered.nextDue > 100)
        val weak = engine.updateMastery(Mastery("q", "t", score = 4), false, 10_000, 100)
        assertEquals(0, weak.score); assertEquals(100L, weak.nextDue); assertEquals(1, weak.mistakes)
    }
    @Test fun `fast correct responses improve mastery more than slow responses`() {
        val m = Mastery("q", "t", score = 20)
        assertTrue(engine.updateMastery(m, true, 20_000, 0).score > engine.updateMastery(m, true, 120_000, 0).score)
    }
    @Test fun `weak slow overdue questions rank ahead of mastered questions`() {
        val weak = Mastery("q", "t", score = 10, attempts = 4, mistakes = 3, averageMillis = 120_000)
        val strong = Mastery("q2", "t", score = 95, attempts = 20, nextDue = Long.MAX_VALUE, averageMillis = 20_000)
        assertTrue(engine.priority(weak, 86_400_000, true) > engine.priority(strong, 86_400_000))
    }
    @Test fun `repetition policy can be changed independently of repository`() {
        assertEquals(30, engine.updateMastery(Mastery("q", "t"), true, 1, 0, RepetitionPolicy(correctGain = 30)).score)
    }
    @Test fun `streak includes yesterday before today is practised`() {
        val today = LocalDate.of(2026, 10, 8)
        assertEquals(2, engine.streak(setOf(today.minusDays(1), today.minusDays(2)), today))
        assertEquals(0, engine.streak(setOf(today.minusDays(2)), today))
        assertEquals(3, engine.streak(setOf(today, today.minusDays(1), today.minusDays(2)), today))
    }
    @Test fun `malformed question metadata is rejected`() {
        assertFalse(question.copy(correctOption = 99).isValid())
        assertFalse(question.copy(id = "../x").isValid())
        assertFalse(question.copy(options = emptyList()).isValid())
        assertTrue(question.isValid())
    }
    @Test fun `test configuration rejects invalid limits`() {
        assertThrows(IllegalArgumentException::class.java) { TestConfig(count = 0) }
        assertThrows(IllegalArgumentException::class.java) { TestConfig(negativeMarks = 2.0) }
    }
    @Test fun `authentication distinguishes initializing from signed out`() {
        assertNotEquals(AuthState.Loading, AuthState.SignedOut)
        assertNotEquals(AuthState.Unconfigured, AuthState.SignedOut)
        assertEquals("u", (AuthState.SignedIn(UserProfile(uid = "u"))).user.uid)
    }
}
