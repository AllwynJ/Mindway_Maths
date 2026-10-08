package com.elsco.mindwaymaths.domain.usecase

import com.elsco.mindwaymaths.domain.model.*
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt
import javax.inject.Inject

data class RepetitionPolicy(
    val targetMillis: Long = 60_000, val correctGain: Int = 16, val slowGain: Int = 7,
    val incorrectLoss: Int = 24, val intervalsDays: List<Long> = listOf(0, 1, 3, 7, 14, 30),
)
class LearningEngine @Inject constructor() {
    fun correct(question: Question, answer: Answer?): Boolean {
        if (answer == null) return false
        return when (question.type) {
            QuestionType.NUMERICAL -> answer.numerical?.trim()?.toDoubleOrNull()?.let {
                it.isFinite() && question.numericalAnswer?.let { expected -> abs(it - expected) <= question.tolerance } == true
            } == true
            else -> answer.option != null && answer.option == question.correctOption
        }
    }
    fun answered(answer: Answer?): Boolean = answer?.option != null || answer?.numerical?.toDoubleOrNull()?.isFinite() == true

    fun updateMastery(old: Mastery, correct: Boolean, millis: Long, now: Long, policy: RepetitionPolicy = RepetitionPolicy()): Mastery {
        val delta = if (!correct) -policy.incorrectLoss else if (millis <= policy.targetMillis) policy.correctGain else policy.slowGain
        val score = (old.score + delta).coerceIn(0, 100)
        val interval = if (!correct) 0 else policy.intervalsDays[(score / 20).coerceAtMost(policy.intervalsDays.lastIndex)]
        return old.copy(score = score, attempts = old.attempts + 1, mistakes = old.mistakes + if (correct) 0 else 1,
            lastSeen = now, nextDue = now + interval * 86_400_000,
            averageMillis = (old.averageMillis * old.attempts + millis.coerceAtLeast(0)) / (old.attempts + 1))
    }
    fun priority(mastery: Mastery?, now: Long, weakTopic: Boolean = false, policy: RepetitionPolicy = RepetitionPolicy()): Double {
        if (mastery == null) return 70.0 + if (weakTopic) 20 else 0
        val overdueDays = ((now - mastery.nextDue).coerceAtLeast(0) / 86_400_000.0).coerceAtMost(30.0)
        return (100 - mastery.score) + overdueDays * 3 +
            (mastery.mistakes.toDouble() / mastery.attempts.coerceAtLeast(1)) * 30 +
            (if (mastery.averageMillis > policy.targetMillis) 15 else 0) + (if (weakTopic) 20 else 0)
    }
    fun score(session: StudySession, now: Long): TestResult {
        val config = requireNotNull(session.config)
        val answered = session.questions.filter { answered(session.answers[it.id]) }
        val right = answered.count { correct(it, session.answers[it.id]) }
        val wrong = answered.size - right
        val max = session.questions.size * config.marks
        val score = right * config.marks - wrong * config.negativeMarks
        fun breakdown(selector: (Question) -> String) = session.questions.groupBy(selector).mapValues { (_, questions) ->
            Breakdown(questions.size, questions.count { correct(it, session.answers[it.id]) }, questions.count { answered(session.answers[it.id]) })
        }
        return TestResult(session.id, session.uid, now, session.questions.size, answered.size, right, wrong,
            session.questions.size - answered.size, score, max, if (answered.isEmpty()) 0.0 else right * 100.0 / answered.size,
            if (max == 0.0) 0.0 else score * 100 / max,
            if (session.questions.isEmpty()) 0 else (now.coerceAtMost(session.deadline ?: now) - session.startedAt).coerceAtLeast(0) / session.questions.size,
            breakdown { it.topic }, breakdown { it.subject })
    }
    fun streak(activeDates: Set<LocalDate>, today: LocalDate): Int {
        var cursor = if (today in activeDates) today else today.minusDays(1)
        var count = 0
        while (cursor in activeDates) { count++; cursor = cursor.minusDays(1) }
        return count
    }
    fun masteryLabel(score: Int) = when (score) { in 0..30 -> "Weak"; in 31..60 -> "Improving"; in 61..80 -> "Good"; else -> "Mastered" }
}
