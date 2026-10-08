package com.elsco.mindwaymaths.worker

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.elsco.mindwaymaths.MainActivity
import com.elsco.mindwaymaths.R
import com.elsco.mindwaymaths.data.local.PreferencesStore
import com.elsco.mindwaymaths.domain.repository.*
import com.elsco.mindwaymaths.security.PrivatePdfCache
import dagger.assisted.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltWorker class SyncWorker @AssistedInject constructor(
    @Assisted context: Context, @Assisted params: WorkerParameters,
    private val repository: LearningRepository, private val auth: AuthRepository, private val cache: PrivatePdfCache,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (auth.uid == null) return Result.success()
        return try { kotlinx.coroutines.withTimeout(90_000) { repository.sync(); cache.prune() }; Result.success() }
        catch (_: kotlinx.coroutines.TimeoutCancellationException) { if (runAttemptCount < 4) Result.retry() else Result.failure() }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { if (runAttemptCount < 4) Result.retry() else Result.failure() }
    }
}
@HiltWorker class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context, @Assisted params: WorkerParameters, private val preferences: PreferencesStore,
    private val auth: AuthRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (auth.uid != null && preferences.claimNotificationSlot(System.currentTimeMillis())) Notifications.show(applicationContext, inputData.getString("kind"))
        return Result.success()
    }
}
object WorkScheduler {
    fun sync(context: Context) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(context).enqueueUniqueWork("sync-now", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("sync-periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(12, TimeUnit.HOURS).setConstraints(constraints).build())
    }
    fun reminders(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) manager.cancelUniqueWork("practice-reminder") else manager.enqueueUniquePeriodicWork("practice-reminder",
            ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS).setInitialDelay(24, TimeUnit.HOURS).build())
    }
    fun cancel(context: Context) { WorkManager.getInstance(context).cancelAllWork() }
}
object Notifications {
    fun show(context: Context, kind: String? = null) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("practice", context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val intent = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val title = when (kind) { "pdf" -> "New study material"; "video" -> "A new lesson is ready"; "challenge" -> "Today’s Challenge is ready"; else -> context.getString(R.string.reminder_title) }
        manager.notify(100, NotificationCompat.Builder(context, "practice").setSmallIcon(R.drawable.ic_mindway)
            .setContentTitle(title).setContentText(context.getString(R.string.reminder_body))
            .setContentIntent(intent).setAutoCancel(true).build())
    }
}
@AndroidEntryPoint class MindwayMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        // Topic subscriptions are managed by FCM and the user's preference; never log/store this token.
        WorkScheduler.sync(this)
    }
    // Use data-only messages. Payloads never open URLs or display untrusted text.
    override fun onMessageReceived(message: RemoteMessage) {
        val kind = message.data["kind"]?.takeIf { it in setOf("pdf", "video", "challenge", "reminder", "streak") } ?: return
        WorkManager.getInstance(this).enqueueUniqueWork("content-notification", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ReminderWorker>().setInputData(workDataOf("kind" to kind)).build())
    }
}
