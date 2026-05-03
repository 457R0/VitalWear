package com.github.cfogrady.vitalwear

import android.app.Application
import androidx.room.Room
import com.github.cfogrady.vitalwear.card.ValidatedCardManager
import com.github.cfogrady.vitalwear.common.log.TinyLogTree
import com.github.cfogrady.vitalwear.data.AppDatabase
import com.github.cfogrady.vitalwear.logs.LogService
import timber.log.Timber

class VitalWearCompanion : Application() {
    lateinit var validatedCardManager: ValidatedCardManager
    val logService = LogService()

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        Timber.plant(TinyLogTree(this))
        Timber.i("Running VitalWear Companion. Version: ${BuildConfig.VERSION_NAME}  ${BuildConfig.VERSION_CODE}")
        val originalExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Timber.e("Vital Wear Companion Version ${BuildConfig.VERSION_NAME}   ${BuildConfig.VERSION_CODE} crashed.")
            Timber.e(throwable, "Thread ${thread.name} failed:")
            TinyLogTree.shutdown()
            originalExceptionHandler?.uncaughtException(thread, throwable)
        }
        buildDependencies()
    }

    private fun buildDependencies() {
        // Card catalog tables have been dropped in migration 3→4; only ValidatedCardEntity remains.
        val database = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "VitalWear")
            .addMigrations(AppDatabase.MIGRATION_3_4)
            .fallbackToDestructiveMigrationFrom(1, 2)   // Very old installs: recreate (prerelease only)
            .build()
        validatedCardManager = ValidatedCardManager(database.validatedCardEntityDao())
    }
}