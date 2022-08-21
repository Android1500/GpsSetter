package com.android1500.gpssetter.room

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.android1500.gpssetter.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object AppModule{




    @Provides
    @Singleton
    fun provideDatabase(application: Application, callback: AppDatabase.Callback)
            = Room.databaseBuilder(application, AppDatabase::class.java, "user_database")
        .allowMainThreadQueries()
        .fallbackToDestructiveMigration()
        .addCallback(callback)
        .build()

    @Provides
    fun providesUserDao(userDatabase: AppDatabase) =
        userDatabase.userDao()

    @ApplicationScope
    @Provides
    @Singleton
    fun providesApplicationScope() = CoroutineScope(SupervisorJob())

}

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class ApplicationScope