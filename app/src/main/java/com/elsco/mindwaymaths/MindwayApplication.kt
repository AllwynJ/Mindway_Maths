package com.elsco.mindwaymaths

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import com.elsco.mindwaymaths.security.AppCheckInstaller
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp class MindwayApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    override val workManagerConfiguration get() = Configuration.Builder().setWorkerFactory(workerFactory)
        .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.INFO else android.util.Log.ERROR).build()
    override fun onCreate() {
        super.onCreate(); instance = this
        if (FirebaseApp.initializeApp(this) != null) AppCheckInstaller.install()
    }
    companion object { lateinit var instance: MindwayApplication; private set }
}
