package com.invincible.jedishare.di

import timber.log.Timber

import android.content.ContentResolver
import android.content.Context
import androidx.room.Room
import com.invincible.jedishare.data.UserPreferencesDataStore
import com.invincible.jedishare.data.chat.AndroidBluetoothController
import com.invincible.jedishare.data.db.JediShareDatabase
import com.invincible.jedishare.data.db.TransferHistoryDao
import com.invincible.jedishare.data.repository.FileTransferRepository
import com.invincible.jedishare.data.repository.MediaRepository
import com.invincible.jedishare.data.repository.TransferHistoryRepository
import com.invincible.jedishare.domain.chat.BluetoothController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ── System Services ────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver =
        context.contentResolver

    // ── Bluetooth ──────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideBluetoothController(@ApplicationContext context: Context): BluetoothController =
        AndroidBluetoothController(context)

    // ── Media Repositories ─────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideMediaRepository(contentResolver: ContentResolver): MediaRepository =
        MediaRepository(contentResolver)

    @Provides
    @Singleton
    fun provideFileTransferRepository(contentResolver: ContentResolver, @ApplicationContext context: Context): FileTransferRepository =
        FileTransferRepository(contentResolver, context)

    // ── Room Database ──────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): JediShareDatabase =
        Room.databaseBuilder(
            context,
            JediShareDatabase::class.java,
            JediShareDatabase.DATABASE_NAME
        )
        .fallbackToDestructiveMigration()
        .build()

    @Provides
    @Singleton
    fun provideTransferHistoryDao(database: JediShareDatabase): TransferHistoryDao =
        database.transferHistoryDao()

    @Provides
    @Singleton
    fun provideTransferHistoryRepository(dao: TransferHistoryDao): TransferHistoryRepository =
        TransferHistoryRepository(dao)

    // ── DataStore ──────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideUserPreferencesDataStore(@ApplicationContext context: Context): UserPreferencesDataStore =
        UserPreferencesDataStore(context)
}