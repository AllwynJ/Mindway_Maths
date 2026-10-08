package com.elsco.mindwaymaths.data.repository

import android.app.Activity
import android.content.Context
import androidx.credentials.*
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.*
import com.google.firebase.auth.*
import com.google.firebase.firestore.SetOptions
import com.elsco.mindwaymaths.BuildConfig
import com.elsco.mindwaymaths.data.local.PreferencesStore
import com.elsco.mindwaymaths.data.remote.FirebaseProvider
import com.elsco.mindwaymaths.domain.model.*
import com.elsco.mindwaymaths.domain.repository.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class FirebaseAuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebase: FirebaseProvider, private val preferences: PreferencesStore,
) : AuthRepository {
    override val uid: String? get() = if (firebase.configured) firebase.auth.currentUser?.uid else null
    private val refresh = MutableStateFlow(0)
    override val state: Flow<AuthState> = callbackFlow {
        if (!firebase.configured) { trySend(AuthState.Unconfigured); close(); return@callbackFlow }
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.let { AuthState.SignedIn(it.profile()) } ?: AuthState.SignedOut)
        }
        firebase.auth.addAuthStateListener(listener)
        awaitClose { firebase.auth.removeAuthStateListener(listener) }
    }.combine(preferences.flow) { auth, prefs ->
        if (auth is AuthState.SignedIn) auth.copy(user = auth.user.copy(selectedExam = prefs.exam,
            selectedLanguage = prefs.language, dailyGoal = prefs.goal)) else auth
    }.combine(refresh) { auth, _ -> auth }.distinctUntilChanged()

    private fun FirebaseUser.profile() = UserProfile(uid, displayName.orEmpty(), email.orEmpty(), photoUrl?.toString(),
        metadata?.creationTimestamp ?: 0, metadata?.lastSignInTimestamp ?: 0, appVersion = BuildConfig.VERSION_NAME)

    private suspend fun credential(activity: Activity, authorizedOnly: Boolean): AuthCredential {
        val clientId = BuildConfig.WEB_CLIENT_ID.ifBlank {
            val resource = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resource != 0) context.getString(resource) else error("Google sign-in is not configured")
        }
        val option = GetGoogleIdOption.Builder().setServerClientId(clientId)
            .setFilterByAuthorizedAccounts(authorizedOnly).setAutoSelectEnabled(false).build()
        val response = CredentialManager.create(context).getCredential(activity, GetCredentialRequest(listOf(option)))
        val result = response.credential as? CustomCredential ?: error("Unsupported sign-in response")
        require(result.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
        return GoogleAuthProvider.getCredential(GoogleIdTokenCredential.createFrom(result.data).idToken, null)
    }
    override suspend fun signIn(activity: Activity) {
        check(firebase.configured)
        val google = try { credential(activity, true) } catch (_: NoCredentialException) { credential(activity, false) }
        val user = firebase.auth.signInWithCredential(google).await().user ?: error("No signed-in user")
        val ref = firebase.store.collection("users").document(user.uid)
        // Authentication remains usable when profile synchronization is temporarily offline.
        try {
            withTimeout(12_000) {
                val existing = ref.get().await()
                if (existing.getBoolean("deletionPending") == true) error("Account deletion is pending. Open Profile to finish deletion.")
                val old = preferences.flow.first()
                preferences.update(old.copy(exam = existing.getString("selectedExam") ?: "",
                    language = existing.getString("selectedLanguage") ?: "en", goal = existing.getLong("dailyGoal")?.toInt() ?: 20))
                val profile = mutableMapOf<String, Any?>("uid" to user.uid, "displayName" to user.displayName.orEmpty(),
                    "email" to user.email.orEmpty(), "photoUrl" to user.photoUrl?.toString(),
                    "lastLoginAt" to System.currentTimeMillis(), "appVersion" to BuildConfig.VERSION_NAME)
                if (!existing.exists()) profile.putAll(mapOf("createdAt" to System.currentTimeMillis(), "selectedExam" to "",
                    "selectedLanguage" to "en", "dailyGoal" to 20, "totalQuestionsSolved" to 0, "totalCorrect" to 0,
                    "totalTests" to 0, "streak" to 0, "deletionPending" to false))
                ref.set(profile, SetOptions.merge()).await()
            }
        } catch (e: kotlinx.coroutines.CancellationException) { if (e !is kotlinx.coroutines.TimeoutCancellationException) throw e }
        refresh.value++
    }
    override suspend fun signOut() {
        if (firebase.configured) firebase.auth.signOut()
        try { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        finally { preferences.clear() }
    }
    override suspend fun updateProfile(exam: String, language: String, goal: Int) {
        val current = preferences.flow.first()
        preferences.update(current.copy(exam = exam, language = language, goal = goal.coerceIn(5, 200)))
        // Firestore queues this small settings update durably when offline.
        uid?.let { firebase.store.collection("users").document(it).set(mapOf("selectedExam" to exam,
            "selectedLanguage" to language, "dailyGoal" to goal.coerceIn(5, 200)), SetOptions.merge()) }
    }
    override suspend fun deleteAccount(activity: Activity) {
        val user = firebase.auth.currentUser ?: error("Sign in again to delete your account")
        user.reauthenticate(credential(activity, false)).await()
        val root = firebase.store.collection("users").document(user.uid)
        val existing = root.get(com.google.firebase.firestore.Source.SERVER).await()
        val marker = if (existing.exists()) mapOf("deletionPending" to true)
            else mapOf("uid" to user.uid, "dailyGoal" to 20, "selectedLanguage" to "en", "deletionPending" to true)
        root.set(marker, SetOptions.merge()).await()
        for (collection in listOf("attempts", "testHistory", "mastery", "bookmarks", "devices")) {
            while (true) {
                val page = root.collection(collection).limit(100).get(com.google.firebase.firestore.Source.SERVER).await()
                if (page.isEmpty) break
                firebase.store.runBatch { batch -> page.documents.forEach { batch.delete(it.reference) } }.await()
            }
        }
        for (collection in listOf("progress", "bookmarks", "settings")) firebase.store.collection(collection).document(user.uid).delete().await()
        root.delete().await()
        user.delete().await()
        signOut()
    }
}
