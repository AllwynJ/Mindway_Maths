package com.elsco.mindwaymaths.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsco.mindwaymaths.R
import com.elsco.mindwaymaths.feature.*
import com.elsco.mindwaymaths.navigation.Screen

@Composable fun HomeScreen(vm: AppViewModel, navigate: (Screen) -> Unit, session: (String) -> Unit) {
    val summary by vm.summary.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val continuing by vm.continuing.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.resumeRefresh() }
    Page {
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("YOUR DAILY EDGE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Heading(stringResource(R.string.ready), "${prefs.exam.ifBlank { "Choose your exam" }.label()} · Make every question count")
        } }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TODAY’S PROGRESS", style = MaterialTheme.typography.labelLarge)
                        Icon(Icons.AutoMirrored.Rounded.TrendingUp, null)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Metric("${summary.today}", "Questions", Modifier.weight(1f))
                        Metric((if (summary.today == 0) 0.0 else summary.todayCorrect * 100.0 / summary.today).percent(), "Accuracy", Modifier.weight(1f))
                        Metric("${summary.streak}", "Day streak", Modifier.weight(1f))
                    }
                    LinearProgressIndicator(progress = { (summary.today.toFloat() / prefs.goal).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(7.dp), color = MaterialTheme.colorScheme.onPrimary,
                        trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = .2f))
                    Text("${summary.today} of ${prefs.goal} questions · Your daily target", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button({ navigate(Screen.PracticeSetup) }, Modifier.weight(1f).heightIn(min = 54.dp)) { Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Practice") }
            OutlinedButton({ navigate(Screen.TestSetup) }, Modifier.weight(1f).heightIn(min = 54.dp)) { Text("Take test") }
        } }
        item { SectionTitle("Continue learning") }
        item { continuing?.let { current -> ActionCard("Pick up where you left off", "Question ${current.index + 1} of ${current.questions.size} · Saved on this device", Icons.Rounded.PlayArrow) { session(current.id) } }
            ?: ActionCard("Build your foundation", "Choose a subject and work through a topic", Icons.Rounded.AutoStories) { navigate(Screen.Subjects) } }
        item { SectionTitle("Recommended for you") }
        item { ActionCard("A little revision, a stronger score", "Revisit questions due for another attempt", Icons.Rounded.Refresh) { navigate(Screen.Revision) } }
        item { SectionTitle("Weak topics") }
        item {
            val weak = summary.topicMastery.filterValues { it <= 30 }.keys.take(2)
            ActionCard(if (weak.isEmpty()) "Find your focus areas" else weak.joinToString(" · ") { it.label() },
                if (weak.isEmpty()) "Start practising to discover where you can improve" else "Target the concepts that need another look", Icons.Rounded.TrackChanges) { navigate(Screen.WeakTopics) }
        }
        item { ActionCard(stringResource(R.string.daily_challenge), "10 questions · 10 minutes · A fresh start every day", Icons.Rounded.Bolt) { navigate(Screen.DailyChallenge) } }
        if (history.isNotEmpty()) item { ActionCard("Your latest test", "${history.first().score} / ${history.first().maxScore} · ${history.first().accuracy.percent()} accuracy", Icons.Rounded.Assessment) { navigate(Screen.TestHistory) } }
        item { SectionTitle("Your study desk") }
        item { ActionCard("Study materials", "Keep the key concepts close", Icons.Rounded.Description) { navigate(Screen.Pdfs) } }
        item { ActionCard("Video lessons", "Understand it. Practise it. Remember it.", Icons.Rounded.PlayCircle) { navigate(Screen.Videos) } }
    }
}
