package com.elsco.mindwaymaths.feature.practice

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsco.mindwaymaths.domain.model.*
import com.elsco.mindwaymaths.feature.*

@Composable fun Choice(label: String, value: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton({ expanded = true }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(options.firstOrNull { it.first == value }?.second ?: value.ifBlank { "All" }.label()) }
            DropdownMenu(expanded, { expanded = false }) { options.forEach { (id, title) ->
                DropdownMenuItem(text = { Text(title) }, onClick = { onSelect(id); expanded = false })
            } }
        }
    }
}
@Composable fun PracticeSetupScreen(vm: AppViewModel, test: Boolean = false, initialMode: PracticeMode = PracticeMode.TOPIC,
    initialTopic: String = "", initialSubject: String = "", start: (String) -> Unit) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val exams by vm.exams.collectAsStateWithLifecycle()
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val topics by vm.topics.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var exam by rememberSaveable { mutableStateOf(prefs.exam.ifBlank { "ssc" }) }
    var subject by rememberSaveable { mutableStateOf(initialSubject) }
    var topic by rememberSaveable { mutableStateOf(initialTopic) }
    var difficulty by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var count by rememberSaveable { mutableIntStateOf(10) }
    var duration by rememberSaveable { mutableIntStateOf(10) }
    var negative by rememberSaveable { mutableStateOf("0.25") }
    val timed = test || mode in setOf(PracticeMode.SPEED, PracticeMode.DAILY)
    val filter = PracticeFilter(exam, subject, topic, difficulty, type, mode)
    Page {
        item { Heading(if (test) "Make it exam day." else if (initialMode == PracticeMode.DAILY) "Today’s Challenge" else "Practice with purpose.",
            if (timed) "A focused session. An honest measure of your progress." else "Choose your focus. Every attempt helps you improve.") }
        item { Choice("Exam", exam, exams.map { it.id to it.title }) { exam = it; topic = "" } }
        item { Choice("Subject", subject, listOf("" to "All subjects") + subjects.map { it.id to it.title }) { subject = it; topic = "" } }
        item { Choice("Topic", topic, listOf("" to "All topics") + topics.filter { subject.isBlank() || it.subject == subject }.map { it.id to it.title }) { topic = it } }
        item { Choice("Difficulty", difficulty, listOf("" to "All levels", "Easy" to "Easy", "Medium" to "Medium", "Hard" to "Hard")) { difficulty = it } }
        item { Choice("Question type", type, listOf("" to "All types") + QuestionType.entries.map { it.name to it.name.label() }) { type = it } }
        item { Choice("Mode", mode.name, PracticeMode.entries.map { it.name to it.name.label() }) { mode = PracticeMode.valueOf(it); if (mode == PracticeMode.DAILY) count = count.coerceIn(5, 20) } }
        if (timed) {
            item { Choice("Questions", count.toString(), listOf(5, 10, 15, 20, 25, 50, 100).filter { mode != PracticeMode.DAILY || it <= 20 }.map { "$it" to "$it questions" }) { count = it.toInt() } }
            item { Choice("Time limit", duration.toString(), listOf(5, 10, 15, 20, 30, 60, 90, 120).map { "$it" to "$it minutes" }) { duration = it.toInt() } }
            item { Choice("Negative marking", negative, listOf("0.0" to "None", "0.25" to "−0.25 per incorrect answer", "0.33" to "−0.33 per incorrect answer", "0.5" to "−0.5 per incorrect answer")) { negative = it } }
            item { Notice("+1 for each correct answer. Skipped questions score 0. If fewer questions are saved, the test uses the available set.") }
        }
        item { Button({ vm.start(filter, if (timed) TestConfig(count, duration, 1.0, negative.toDouble(), filter) else null, start) },
            Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = !busy) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text(if (timed) "Start timed test" else "Start practice")
        } }
        item { TextButton({ vm.downloadMore(filter) }, enabled = !busy) { Text("Save more questions for offline practice") } }
    }
}
@Composable fun CatalogScreen(vm: AppViewModel, collection: String, onSelect: (CatalogItem) -> Unit) {
    val entries by when (collection) { "exams" -> vm.exams; "subjects" -> vm.subjects; else -> vm.topics }.collectAsStateWithLifecycle()
    val loading by vm.loadingContent.collectAsStateWithLifecycle()
    val errors by vm.contentErrors.collectAsStateWithLifecycle()
    val more by vm.moreAvailable.collectAsStateWithLifecycle()
    LaunchedEffect(collection) { vm.refresh(collection) }
    Page {
        item { Heading("Choose your ${collection.removeSuffix("s")}", "A focused path to a stronger score.") }
        item { ContentStatus(collection in loading, entries.isEmpty(), collection in errors) { vm.refresh(collection) } }
        items(entries, key = { it.id }) { entry -> ActionCard(entry.title, entry.subject.ifBlank { "Explore topics and practice" }.label()) { onSelect(entry) } }
        if (collection in more) item { TextButton({ vm.refresh(collection, true) }) { Text("Load more") } }
    }
}
