package com.elsco.mindwaymaths.feature.practice

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.elsco.mindwaymaths.R
import com.elsco.mindwaymaths.domain.model.*
import com.elsco.mindwaymaths.feature.*

@Composable fun QuestionScreen(vm: SessionViewModel, onBack: () -> Unit, practiceAgain: (String) -> Unit) {
    val session by vm.session.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val remaining by vm.remaining.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var confirm by rememberSaveable { mutableStateOf(false) }
    var leave by rememberSaveable { mutableStateOf(false) }
    var reviewResults by rememberSaveable { mutableStateOf(false) }
    val current = session
    if (current == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    BackHandler(current.config != null && !current.completed) { leave = true }
    if (result != null && !reviewResults) { TestResultScreen(requireNotNull(result), { reviewResults = true }, onBack); return }
    val question = current.questions.getOrNull(current.index)
    if (question == null) { Notice("This question set is empty.", onBack); return }
    val answer = current.answers[question.id]
    val explained = question.id in current.submittedQuestions || current.completed
    Page {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(question.topic.label(), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.question_position, current.index + 1, current.questions.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (current.config != null && !current.completed) AssistChip({}, { Text(remaining.clock()) }, leadingIcon = { Icon(Icons.Rounded.Timer, null, Modifier.size(18.dp)) })
        } }
        item { LinearProgressIndicator(progress = { (current.index + 1f) / current.questions.size }, modifier = Modifier.fillMaxWidth()) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SuggestionChip({}, { Text(question.difficulty) })
            question.year?.let { SuggestionChip({}, { Text("PYQ $it") }) }
            if (question.id in current.review) SuggestionChip({}, { Text("Marked for review") })
        } }
        item { Text(question.questionText, style = MaterialTheme.typography.titleLarge) }
        question.imageUrl?.takeIf { it.startsWith("https://") }?.let { url -> item { AsyncImage(url, "Question diagram", Modifier.fillMaxWidth().heightIn(max = 250.dp)) } }
        if (question.type == QuestionType.NUMERICAL) item {
            OutlinedTextField(answer?.numerical.orEmpty(), { vm.answer(numerical = it.take(32)) }, Modifier.fillMaxWidth(),
                label = { Text("Your numerical answer") }, enabled = !explained && !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        } else items(question.options.indices.toList(), key = { "option-$it" }) { index ->
            val selected = answer?.option == index
            val correctOption = explained && question.correctOption == index
            val container = when { correctOption -> MaterialTheme.colorScheme.primaryContainer; selected -> MaterialTheme.colorScheme.secondaryContainer; else -> MaterialTheme.colorScheme.surfaceContainerLow }
            Card(onClick = { vm.answer(option = index) }, enabled = !explained && !busy, modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = container, disabledContainerColor = container), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RadioButton(selected, null, enabled = !explained)
                    Column(Modifier.weight(1f)) {
                        Text("${('A'.code + index).toChar()}. ${question.options[index]}", color = MaterialTheme.colorScheme.onSurface)
                        if (correctOption) Text("Correct answer", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        else if (explained && selected) Text("Your answer · Incorrect", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        if (explained) item { AnswerExplanationScreen(question, vm.engine.correct(question, answer), answer?.timeMillis ?: 0) }
        if (error != null) item { Notice(requireNotNull(error)) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(vm::bookmark, Modifier.weight(1f), enabled = !busy) { Text(if (question.id in bookmarks) "Bookmarked" else "Bookmark") }
            if (current.config != null && !current.completed) OutlinedButton(vm::review, Modifier.weight(1f)) { Text(if (question.id in current.review) "Unmark review" else "Mark review") }
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton({ vm.move(current.index - 1) }, Modifier.weight(1f), enabled = current.index > 0 && !busy) { Text("Previous") }
            if (!explained && current.config == null) Button(vm::submitAnswer, Modifier.weight(1f), enabled = vm.engine.answered(answer) && !busy) { Text("Check answer") }
            else if (current.index < current.questions.lastIndex) Button({ vm.move(current.index + 1) }, Modifier.weight(1f), enabled = !busy) { Text("Next") }
            else if (current.config == null) Button({ vm.finish(); onBack() }, Modifier.weight(1f)) { Text("Finish practice") }
        } }
        if (current.config != null && !current.completed) {
            item { SectionTitle("Answer palette · ${current.answers.values.count { vm.engine.answered(it) }} answered · ${current.review.size} marked") }
            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                current.questions.forEachIndexed { index, q ->
                    FilterChip(index == current.index, { vm.move(index) }, { Text("${index + 1}${if (q.id in current.review) " ⚑" else if (vm.engine.answered(current.answers[q.id])) " ✓" else ""}") })
                }
            } }
            item { Button({ confirm = true }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Submit test") } }
        }
        if (explained) item { TextButton({ practiceAgain(question.topic) }) { Text("Practice again") } }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Submit this test?") },
        text = { Text("${current.questions.size - current.answers.values.count { vm.engine.answered(it) }} questions are unanswered. Your result will be final.") },
        confirmButton = { TextButton({ confirm = false; vm.finish() }) { Text("Submit test") } }, dismissButton = { TextButton({ confirm = false }) { Text("Keep working") } })
    if (leave) AlertDialog(onDismissRequest = { leave = false }, title = { Text("Leave this test?") }, text = { Text("Your answers are saved. The timer continues while you’re away.") },
        confirmButton = { TextButton({ leave = false; onBack() }) { Text("Save and leave") } }, dismissButton = { TextButton({ leave = false }) { Text("Continue test") } })
}
@Composable fun AnswerExplanationScreen(question: Question, correct: Boolean, time: Long) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (correct) "Correct · Nicely done" else "Let’s work through it", style = MaterialTheme.typography.titleMedium)
            if (question.type == QuestionType.NUMERICAL) Text("Correct answer: ${question.numericalAnswer}")
            Text(question.explanation)
            if (question.solution.isNotBlank()) Text(question.solution, style = MaterialTheme.typography.bodyMedium)
            if (question.shortcut.isNotBlank()) { Text("THE SHORTCUT", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary); Text(question.shortcut) }
            Text("Time taken ${time.clock()} · ${question.source}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
@Composable fun TestResultScreen(result: TestResult, review: () -> Unit, done: () -> Unit) {
    Page {
        item { Heading("Every test is a step forward.", "Here’s where you stand, and where to focus next.") }
        item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("YOUR SCORE", style = MaterialTheme.typography.labelLarge)
                Text("${"%.2f".format(result.score)} / ${result.maxScore}", style = MaterialTheme.typography.headlineLarge)
                Text("${result.percentage.percent()} of maximum · ${result.accuracy.percent()} accuracy")
                Text("${result.averageMillis.clock()} average per question")
            }
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("${result.correct}", "Correct"); Metric("${result.incorrect}", "Incorrect"); Metric("${result.skipped}", "Skipped")
        } }
        item { SectionTitle("Topic performance") }
        items(result.topics.entries.toList()) { (topic, score) ->
            Notice("${topic.label()} · ${score.correct}/${score.total} correct${if (score.correct * 2 < score.total) " · Focus area" else ""}")
        }
        item { SectionTitle("Subject performance") }
        items(result.subjects.entries.toList()) { (subject, score) -> Text("${subject.label()}: ${score.correct} correct · ${score.attempted} attempted") }
        item { Button(review, Modifier.fillMaxWidth()) { Text("Review answers and explanations") } }
        item { OutlinedButton(done, Modifier.fillMaxWidth()) { Text("Back to learning") } }
    }
}
