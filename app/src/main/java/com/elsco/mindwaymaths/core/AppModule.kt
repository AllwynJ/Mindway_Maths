package com.elsco.mindwaymaths.core

import android.content.Context
import androidx.room.Room
import com.elsco.mindwaymaths.data.local.*
import com.elsco.mindwaymaths.data.repository.*
import com.elsco.mindwaymaths.domain.repository.*
import dagger.*
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun json() = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    @Provides @Singleton fun database(@ApplicationContext context: Context) = Room.databaseBuilder(context, MindwayDatabase::class.java, "mindway.db").build()
    @Provides fun dao(database: MindwayDatabase) = database.learningDao()
    @Provides fun auth(repository: FirebaseAuthRepository): AuthRepository = repository
    @Provides fun learning(repository: OfflineLearningRepository): LearningRepository = repository
}
