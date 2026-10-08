package com.elsco.mindwaymaths.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsco.mindwaymaths.feature.*
import com.elsco.mindwaymaths.domain.usecase.LearningEngine
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable fun ProgressScreen(vm: AppViewModel, history: () -> Unit, weak: () -> Unit, incorrect: () -> Unit, bookmarks: () -> Unit) {
    val summary by vm.summary.collectAsStateWithLifecycle()
    Page {
        item { Heading("See how far you’ve come.", "Consistency counts. Your progress is personal.") }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("${summary.solved}", "Questions"); Metric(summary.accuracy.percent(), "Accuracy"); Metric("${summary.streak}", "Day streak")
        } }
        item { Notice("Average solving time: ${summary.averageMillis.clock()} · ${summary.tests} tests completed") }
        item { SectionTitle("This week") }
        item {
            val days = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
            val max = days.maxOf { summary.dailyActivity[it.toString()] ?: 0 }.coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                days.forEach { date ->
                    val count = summary.dailyActivity[date.toString()] ?: 0
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "$date: $count questions" },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("$count", style = MaterialTheme.typography.labelSmall)
                        Box(Modifier.fillMaxWidth().height((count.toFloat() / max * 90).coerceAtLeast(4f).dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)))
                        Text(date.format(DateTimeFormatter.ofPattern("EEEEE")), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { val month = LocalDate.now().toString().take(7)
            Notice("This month · ${summary.dailyActivity.filterKeys { it.startsWith(month) }.values.sum()} questions across ${summary.dailyActivity.keys.count { it.startsWith(month) }} active days") }
        item { SectionTitle("Topic mastery") }
        if (summary.topicMastery.isEmpty()) item { Notice("Complete a practice session to start building your mastery map.") }
        items(summary.topicMastery.entries.sortedBy { it.value }) { (topic, score) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${topic.label()} · $score/100 · ${LearningEngine().masteryLabel(score)}")
                LinearProgressIndicator(progress = { score / 100f }, Modifier.fillMaxWidth())
            }
        }
        item { ActionCard("Test history", "Review your results and explanations", onClick = history) }
        item { ActionCard("Weak topics", "A targeted next step", onClick = weak) }
        item { ActionCard("Incorrect questions", "${summary.incorrect} questions to revisit", onClick = incorrect) }
        item { ActionCard("Bookmarks", "${summary.bookmarks} saved questions", onClick = bookmarks) }
        item { TextButton(vm::sync) { Text("Sync progress") } }
    }
}
@Composable fun TestHistoryScreen(vm: AppViewModel, open: (String) -> Unit) {
    val results by vm.history.collectAsStateWithLifecycle()
    Page {
        item { Heading("Your test journey.", "Every result is a chance to improve.") }
        if (results.isEmpty()) item { Notice("No tests yet. Your first result will appear here.") }
        items(results, key = { it.sessionId }) { result ->
            ActionCard("${"%.2f".format(result.score)} / ${result.maxScore}", "${result.total} questions · ${result.accuracy.percent()} accuracy · ${java.time.Instant.ofEpochMilli(result.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}") { open(result.sessionId) }
        }
    }
}
