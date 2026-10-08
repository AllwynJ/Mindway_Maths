package com.elsco.mindwaymaths.feature.profile

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.elsco.mindwaymaths.BuildConfig
import com.elsco.mindwaymaths.R
import com.elsco.mindwaymaths.domain.model.AuthState
import com.elsco.mindwaymaths.feature.*
import com.elsco.mindwaymaths.feature.practice.Choice
import com.elsco.mindwaymaths.navigation.Screen

@Composable fun ProfileScreen(vm: AppViewModel, activity: Activity, navigate: (Screen) -> Unit) {
    val auth by vm.auth.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val user = (auth as? AuthState.SignedIn)?.user ?: return
    var deletion by rememberSaveable { mutableStateOf(false) }
    var logout by rememberSaveable { mutableStateOf(false) }
    Page {
        item { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            if (user.photoUrl != null) AsyncImage(user.photoUrl, "Profile photo", Modifier.size(64.dp).clip(CircleShape))
            else Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) { Text(user.displayName.take(1).ifBlank { "M" }, Modifier.padding(22.dp)) }
            Column { Text(user.displayName.ifBlank { "Your profile" }, style = MaterialTheme.typography.titleLarge); Text(user.email, style = MaterialTheme.typography.bodyMedium) }
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("${summary.solved}", "Questions"); Metric("${summary.tests}", "Tests"); Metric(summary.accuracy.percent(), "Accuracy")
        } }
        item { Notice("${summary.streak} day streak · Keep showing up for yourself.") }
        item { ActionCard("Your exam", user.selectedExam.ifBlank { "Choose your exam" }.label()) { navigate(Screen.Exams) } }
        item { ActionCard("Settings", "Daily goal, appearance and reminders") { navigate(Screen.Settings) } }
        item { ActionCard("Privacy policy", "How your information is handled") { navigate(Screen.Privacy) } }
        item { ActionCard("Terms of service", "Publisher’s terms") { navigate(Screen.Terms) } }
        item { ActionCard("About Mindway Maths", "A focused way to prepare") { navigate(Screen.About) } }
        item { OutlinedButton({ logout = true }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Log out / switch account") } }
        item { TextButton({ deletion = true }, enabled = !busy) { Text("Delete account", color = MaterialTheme.colorScheme.error) } }
    }
    if (logout) AlertDialog(onDismissRequest = { logout = false }, title = { Text("Log out?") }, text = { Text("Saved progress remains on this device for your account. Unsynced work uploads when you sign back in. Private PDF copies will be removed.") },
        confirmButton = { TextButton({ logout = false; vm.logout() }) { Text("Log out") } }, dismissButton = { TextButton({ logout = false }) { Text("Cancel") } })
    if (deletion) AlertDialog(onDismissRequest = { deletion = false }, title = { Text("Permanently delete your account?") },
        text = { Text("Your profile, attempts, progress, bookmarks and test history will be deleted. This cannot be undone. You’ll confirm your Google account first. Stay online until deletion finishes. If interrupted, sign in and repeat this action to finish.") },
        confirmButton = { TextButton({ deletion = false; vm.delete(activity) }) { Text("Delete permanently", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ deletion = false }) { Text("Keep account") } })
}
@Composable fun SettingsScreen(vm: AppViewModel) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.settings(prefs.copy(notifications = granted)) }
    Page {
        item { Heading("Make it your routine.") }
        item { Choice("Appearance", prefs.theme, listOf("system" to "Use device setting", "light" to "Light", "dark" to "Dark")) { vm.settings(prefs.copy(theme = it)) } }
        item { Choice("Daily goal", prefs.goal.toString(), listOf(5, 10, 20, 30, 50, 100, 200).map { "$it" to "$it questions" }) { vm.updateProfile(goal = it.toInt()) } }
        item { Choice("Content language", prefs.language, listOf("en" to "English")) { vm.updateProfile(language = it) } }
        item { Notice("More languages can be added through Android string resources and translated content collections.") }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Practice reminders", style = MaterialTheme.typography.titleMedium); Text("At most one gentle daily reminder", style = MaterialTheme.typography.bodyMedium) }
            Switch(prefs.notifications, { enabled -> if (enabled && Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.settings(prefs.copy(notifications = enabled)) })
        } }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Share crash diagnostics", Modifier.weight(1f)); Switch(prefs.crashReporting, { vm.settings(prefs.copy(crashReporting = it)) })
        } }
        item { Choice("Private PDF cache retention", prefs.pdfCacheDays.toString(), listOf(1, 3, 7, 14, 30).map { "$it" to "$it days" }) { vm.settings(prefs.copy(pdfCacheDays = it.toInt())) } }
    }
}
@Composable fun PrivacyScreen(title: String = "Privacy policy") {
    Page { item { Heading(title) }; item { Notice(stringResource(R.string.legal_placeholder)) }
        item { Text("Implementation data inventory", style = MaterialTheme.typography.titleMedium) }
        item { Text("Google account identifier, name, email and optional profile image; selected exam and preferences; question attempts, bookmarks, mastery and test history. Private PDFs are cached in app-private encrypted storage. Video playback connects to YouTube under its own policies.") }
        item { Text("Account deletion", style = MaterialTheme.typography.titleMedium) }
        item { Text("Use Profile → Delete account while connected to the internet. Confirm the same Google account. The app deletes user records, then the Firebase account, and removes local user data. A publisher-provided public web deletion request link is also required for store distribution.") }
    }
}
@Composable fun AboutScreen() { Page {
    item { Heading("Mindway Maths", "Small steps. Stronger scores.") }
    item { Text("Focused quantitative aptitude and logical reasoning practice for competitive exams.") }
    item { Text("Version ${BuildConfig.VERSION_NAME}") }
    item { Notice("Content protection combines authenticated delivery, app-private storage and secure windows. It is a deterrence system, not unbreakable DRM.") }
} }
