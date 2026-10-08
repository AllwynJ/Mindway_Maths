package com.elsco.mindwaymaths.feature

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessaging
import com.elsco.mindwaymaths.core.ConnectivityMonitor
import com.elsco.mindwaymaths.data.local.Preferences
import com.elsco.mindwaymaths.data.local.PreferencesStore
import com.elsco.mindwaymaths.data.remote.FirebaseProvider
import com.elsco.mindwaymaths.domain.model.*
import com.elsco.mindwaymaths.domain.repository.*
import com.elsco.mindwaymaths.security.PrivatePdfCache
import com.elsco.mindwaymaths.worker.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel class AppViewModel @Inject constructor(
    private val authRepository: AuthRepository, val learning: LearningRepository,
    private val store: PreferencesStore, connectivity: ConnectivityMonitor, private val pdfCache: PrivatePdfCache,
    private val firebase: FirebaseProvider, @ApplicationContext private val context: Context,
) : ViewModel() {
    val auth = authRepository.state.stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Loading)
    private val mutablePreferences = MutableStateFlow(Preferences())
    val preferences = mutablePreferences.asStateFlow()
    val preferencesReady = MutableStateFlow(false)
    val online = connectivity.online.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    private val signedIn = auth.map { (it as? AuthState.SignedIn)?.user?.uid }.distinctUntilChanged()
    val summary = signedIn.flatMapLatest { if (it == null) flowOf(ProgressSummary()) else learning.progress() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressSummary())
    val history = signedIn.flatMapLatest { if (it == null) flowOf(emptyList()) else learning.history() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val exams = learning.catalog("exams").stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val subjects = learning.catalog("subjects").stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val topics = learning.catalog("topics").stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val videos = learning.videos().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pdfs = learning.pdfs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val loadingContent = MutableStateFlow<Set<String>>(emptySet())
    val contentErrors = MutableStateFlow<Set<String>>(emptySet())
    val moreAvailable = MutableStateFlow<Set<String>>(emptySet())
    val continuing = MutableStateFlow<StudySession?>(null)
    init {
        viewModelScope.launch { store.flow.collect { mutablePreferences.value = it; preferencesReady.value = true } }
        viewModelScope.launch { store.flow.map { it.crashReporting }.distinctUntilChanged().collect { enabled ->
            if (firebase.configured) FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
        } }
        viewModelScope.launch { signedIn.collect { uid ->
            if (uid != null) {
                WorkScheduler.sync(context)
                continuing.value = learning.latestSession()
                listOf("exams", "subjects", "topics").forEach { refresh(it) }
            } else continuing.value = null
        } }
    }
    private fun action(block: suspend () -> Unit) { if (busy.value) return; viewModelScope.launch {
        busy.value = true; message.value = null
        try { block() } catch (e: CancellationException) { throw e }
        catch (_: androidx.credentials.exceptions.GetCredentialCancellationException) { }
        catch (_: Exception) { message.value = "Couldn’t complete that action. Check your connection and try again. For account deletion, sign in with the same Google account." }
        finally { busy.value = false }
    } }
    fun signIn(activity: Activity) = action { authRepository.signIn(activity) }
    fun logout() = action { WorkScheduler.cancel(context); pdfCache.clear(); authRepository.signOut() }
    fun delete(activity: Activity) = action {
        val uid = requireNotNull(authRepository.uid)
        WorkScheduler.cancel(context)
        withTimeout(120_000) { authRepository.deleteAccount(activity) }
        learning.clearUser(uid); pdfCache.clear()
    }
    fun updateProfile(exam: String = preferences.value.exam, language: String = preferences.value.language, goal: Int = preferences.value.goal) = action {
        authRepository.updateProfile(exam, language, goal)
    }
    fun settings(value: Preferences) = action {
        store.update(value)
        WorkScheduler.reminders(context, value.notifications)
        if (firebase.configured) {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(value.crashReporting)
            FirebaseMessaging.getInstance().isAutoInitEnabled = value.notifications
            if (value.notifications) FirebaseMessaging.getInstance().subscribeToTopic("learning-updates")
            else FirebaseMessaging.getInstance().unsubscribeFromTopic("learning-updates")
        }
    }
    fun refresh(collection: String, next: Boolean = false) {
        if (collection in loadingContent.value || authRepository.uid == null) return
        viewModelScope.launch {
            loadingContent.value += collection; contentErrors.value -= collection
            try {
                val more = learning.refreshCatalog(collection, next)
                moreAvailable.value = if (more) moreAvailable.value + collection else moreAvailable.value - collection
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { contentErrors.value += collection }
            finally { loadingContent.value -= collection }
        }
    }
    fun start(filter: PracticeFilter, config: TestConfig?, navigate: (String) -> Unit) = action {
        val saved = try { learning.createSession(filter, config) } catch (_: IllegalArgumentException) { null }
        if (saved != null) {
            continuing.value = saved; navigate(saved.id)
            return@action
        }
        if (online.value) {
            try { learning.refreshQuestions(filter) } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message.value = "Using saved questions. New content couldn’t be loaded." }
        }
        try { val session = learning.createSession(filter, config); continuing.value = session; navigate(session.id) }
        catch (e: IllegalArgumentException) { message.value = "No saved questions match. Choose a topic and refresh online, or try a different mode." }
    }
    fun downloadMore(filter: PracticeFilter) = action {
        val more = learning.refreshQuestions(filter, true)
        message.value = if (more) "More questions saved. You can load the next page." else "This question set is up to date."
    }
    fun resumeRefresh() { viewModelScope.launch { if (authRepository.uid != null) continuing.value = learning.latestSession() } }
    fun sync() { WorkScheduler.sync(context); message.value = "Sync scheduled. Your saved progress stays available." }
}
