package com.elsco.mindwaymaths.navigation

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.*
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.elsco.mindwaymaths.R
import com.elsco.mindwaymaths.core.MindwayTheme
import com.elsco.mindwaymaths.domain.model.*
import com.elsco.mindwaymaths.feature.*
import com.elsco.mindwaymaths.feature.auth.*
import com.elsco.mindwaymaths.feature.home.HomeScreen
import com.elsco.mindwaymaths.feature.pdfs.*
import com.elsco.mindwaymaths.feature.practice.*
import com.elsco.mindwaymaths.feature.profile.*
import com.elsco.mindwaymaths.feature.progress.*
import com.elsco.mindwaymaths.feature.videos.*
import kotlinx.serialization.Serializable

@androidx.annotation.Keep
@Serializable enum class Screen {
    Home, Exams, Subjects, Topics, PracticeSetup, TestSetup, TestHistory, Videos, Pdfs, Progress,
    Bookmarks, Incorrect, WeakTopics, DailyChallenge, Profile, Settings, Privacy, Terms, About, Revision, More,
}
@Serializable data class ScreenRoute(val screen: Screen = Screen.Home, val topic: String = "", val subject: String = "")
@Serializable data class SessionRoute(val id: String)
@Serializable data class PdfRoute(val id: String)
@Serializable data class VideoRoute(val id: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MindwayApp(vm: AppViewModel = hiltViewModel()) {
    val auth by vm.auth.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val preferencesReady by vm.preferencesReady.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val activity = requireNotNull(LocalActivity.current)
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    MindwayTheme(prefs.theme) {
        Surface(Modifier.fillMaxSize()) {
            if (auth == AuthState.Loading || !preferencesReady) SplashScreen()
            else if (auth !is AuthState.SignedIn) {
                var legal by rememberSaveable { mutableStateOf(false) }
                Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding -> Box(Modifier.padding(padding)) {
                    LoginScreen(auth != AuthState.Unconfigured, busy, { vm.signIn(activity) }, { legal = true })
                    if (legal) AlertDialog(onDismissRequest = { legal = false }, title = { Text("Privacy and terms") },
                        text = { Text(stringResource(R.string.legal_placeholder)) }, confirmButton = { TextButton({ legal = false }) { Text("Close") } })
                } }
            } else key((auth as AuthState.SignedIn).user.uid) {
                val nav = rememberNavController()
                val backStack by nav.currentBackStackEntryAsState()
                val route = backStack?.takeIf { it.destination.hasRoute<ScreenRoute>() }?.toRoute<ScreenRoute>()
                val screen = route?.screen
                fun navigate(target: Screen) { if (route?.screen != target) nav.navigate(ScreenRoute(target)) }
                fun tab(target: Screen) {
                    if (target == Screen.Home && nav.popBackStack(ScreenRoute(Screen.Home), inclusive = false)) return
                    nav.navigate(ScreenRoute(target)) {
                        // Match the filled route, not its shared destination ID. Restoring by ID
                        // would restore Home's arguments for every ScreenRoute destination.
                        popUpTo(ScreenRoute(Screen.Home)) { inclusive = target == Screen.Home }
                    }
                }
                Scaffold(
                    topBar = { TopAppBar(title = { Text(if (screen == Screen.Home) "Mindway Maths" else screen?.name?.label() ?: "Mindway Maths", style = MaterialTheme.typography.titleLarge) },
                        navigationIcon = { if (screen != Screen.Home) IconButton({ if (!nav.popBackStack()) tab(Screen.Home) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                        actions = { IconButton({ navigate(Screen.Profile) }) { Icon(Icons.Rounded.AccountCircle, "Profile") } }) },
                    bottomBar = {
                        if (screen != null) NavigationBar {
                            listOf(Triple(Screen.Home, Icons.Rounded.Home, "Home"), Triple(Screen.PracticeSetup, Icons.Rounded.Edit, "Practice"),
                                Triple(Screen.TestSetup, Icons.Rounded.Timer, "Tests"), Triple(Screen.Progress, Icons.Rounded.BarChart, "Progress"), Triple(Screen.More, Icons.Rounded.Apps, "More"))
                                .forEach { (target, icon, label) -> NavigationBarItem(screen == target, { tab(target) }, icon = { Icon(icon, null) }, label = { Text(label) }, modifier = Modifier.testTag("nav-${target.name}")) }
                        }
                    }, snackbarHost = { SnackbarHost(snackbar) },
                ) { padding -> Column(Modifier.padding(padding).fillMaxSize()) {
                    if (!online) Text(stringResource(R.string.offline), Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
                    NavHost(nav, startDestination = ScreenRoute(if (prefs.exam.isBlank()) Screen.Exams else Screen.Home), modifier = Modifier.weight(1f)) {
                        composable<ScreenRoute> { entry ->
                            val current = entry.toRoute<ScreenRoute>()
                            val openSession: (String) -> Unit = { nav.navigate(SessionRoute(it)) }
                            when (current.screen) {
                                Screen.Home -> HomeScreen(vm, ::navigate, openSession)
                                Screen.Exams -> CatalogScreen(vm, "exams") { vm.updateProfile(exam = it.id); nav.navigate(ScreenRoute(Screen.Home)) { popUpTo<ScreenRoute> { inclusive = true } } }
                                Screen.Subjects -> CatalogScreen(vm, "subjects") { nav.navigate(ScreenRoute(Screen.Topics, subject = it.id)) }
                                Screen.Topics -> CatalogScreen(vm, "topics") { nav.navigate(ScreenRoute(Screen.PracticeSetup, topic = it.id, subject = it.subject)) }
                                Screen.PracticeSetup, Screen.TestSetup, Screen.Bookmarks, Screen.Incorrect, Screen.WeakTopics, Screen.DailyChallenge, Screen.Revision ->
                                    PracticeSetupScreen(vm, current.screen == Screen.TestSetup, when (current.screen) {
                                        Screen.Bookmarks -> PracticeMode.BOOKMARKS; Screen.Incorrect -> PracticeMode.INCORRECT
                                        Screen.WeakTopics -> PracticeMode.WEAK; Screen.DailyChallenge -> PracticeMode.DAILY; Screen.Revision -> PracticeMode.REVISION
                                        else -> PracticeMode.TOPIC
                                    }, current.topic, current.subject, openSession)
                                Screen.TestHistory -> TestHistoryScreen(vm, openSession)
                                Screen.Videos -> VideoListScreen(vm) { nav.navigate(VideoRoute(it)) }
                                Screen.Pdfs -> PdfListScreen(vm) { nav.navigate(PdfRoute(it)) }
                                Screen.Progress -> ProgressScreen(vm, { navigate(Screen.TestHistory) }, { navigate(Screen.WeakTopics) }, { navigate(Screen.Incorrect) }, { navigate(Screen.Bookmarks) })
                                Screen.Profile -> ProfileScreen(vm, activity, ::navigate)
                                Screen.Settings -> SettingsScreen(vm)
                                Screen.Privacy -> PrivacyScreen()
                                Screen.Terms -> PrivacyScreen("Terms of service")
                                Screen.About -> AboutScreen()
                                Screen.More -> Page {
                                    item { Heading("Your study desk.") }
                                    item { ActionCard("Video lessons", "Watch, understand, practise", Icons.Rounded.PlayCircle) { navigate(Screen.Videos) } }
                                    item { ActionCard("PDF study materials", "Your private revision shelf", Icons.Rounded.Description) { navigate(Screen.Pdfs) } }
                                    item { ActionCard("Daily challenge", "Build your daily habit", Icons.Rounded.Bolt) { navigate(Screen.DailyChallenge) } }
                                    item { ActionCard("Bookmarks", "Return to your saved questions", Icons.Rounded.Bookmark) { navigate(Screen.Bookmarks) } }
                                    item { ActionCard("Profile", "Your exam and preferences", Icons.Rounded.AccountCircle) { navigate(Screen.Profile) } }
                                }
                            }
                        }
                        composable<SessionRoute> { QuestionScreen(hiltViewModel(), { nav.popBackStack() }) { topic -> nav.navigate(ScreenRoute(Screen.PracticeSetup, topic)) } }
                        composable<PdfRoute> { entry ->
                            val pdfs by vm.pdfs.collectAsStateWithLifecycle()
                            PdfViewerScreen(hiltViewModel(), pdfs.find { it.id == entry.toRoute<PdfRoute>().id }?.title ?: "Study material")
                        }
                        composable<VideoRoute> { entry ->
                            val videos by vm.videos.collectAsStateWithLifecycle()
                            VideoPlayerScreen(videos.find { it.id == entry.toRoute<VideoRoute>().id },
                                { topic -> nav.navigate(ScreenRoute(Screen.PracticeSetup, topic)) }, { navigate(Screen.Pdfs) })
                        }
                    }
                } }
            }
        }
    }
}
