package com.blackandblue.justshare.di

import timber.log.Timber

import android.content.ContentResolver
import android.content.Context
import androidx.room.Room
import com.blackandblue.justshare.data.UserPreferencesDataStore
import com.blackandblue.justshare.data.billing.BillingClientWrapper
import com.blackandblue.justshare.data.billing.PurchaseRepository
import com.blackandblue.justshare.data.chat.AndroidBluetoothController
import com.blackandblue.justshare.data.db.JediShareDatabase
import com.blackandblue.justshare.data.db.TransferHistoryDao
import com.blackandblue.justshare.data.remote.QuotaApiService
import com.blackandblue.justshare.data.remote.TelemetryService
import com.blackandblue.justshare.data.repository.FileTransferRepository
import com.blackandblue.justshare.data.repository.MediaRepository
import com.blackandblue.justshare.data.repository.QuotaRepository
import com.blackandblue.justshare.data.repository.TransferHistoryRepository
import com.blackandblue.justshare.domain.chat.BluetoothController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
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
        .addMigrations(JediShareDatabase.MIGRATION_2_3)
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

    // ── AlterSend Remote — API & Quota ─────────────────────────────────────────

    /**
     * Base URL for the AlterSend Remote quota and purchase verification API.
     * Override via a BuildConfig field or hardcode your Cloud Run service URL here
     * once it is deployed.
     */
    @Provides
    @Singleton
    @Named("quotaApiBaseUrl")
    fun provideQuotaApiBaseUrl(): String =
        // TODO: Replace with your deployed Cloud Run service URL before release.
        // Example: "https://just-share-api-<hash>-uc.a.run.app"
        "https://just-share-api.example.com"

    @Provides
    @Singleton
    fun provideQuotaApiService(@Named("quotaApiBaseUrl") baseUrl: String): QuotaApiService =
        QuotaApiService(baseUrl)

    @Provides
    @Singleton
    fun provideQuotaRepository(
        apiService: QuotaApiService,
        dataStore: UserPreferencesDataStore
    ): QuotaRepository = QuotaRepository(apiService, dataStore)

    // ── Google Play Billing ────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideBillingClientWrapper(
        @ApplicationContext context: Context
    ): BillingClientWrapper = BillingClientWrapper(context)

    @Provides
    @Singleton
    fun providePurchaseRepository(
        billingClientWrapper: BillingClientWrapper,
        quotaRepository: QuotaRepository,
        apiService: QuotaApiService,
        dataStore: UserPreferencesDataStore,
        @Named("quotaApiBaseUrl") baseUrl: String
    ): PurchaseRepository = PurchaseRepository(
        billingClientWrapper, quotaRepository, apiService, dataStore, baseUrl
    ).also { it.startObserving() }

    // ── Telemetry ───────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideTelemetryService(
        apiService: QuotaApiService,
        dataStore: UserPreferencesDataStore
    ): TelemetryService = TelemetryService(apiService, dataStore)
}
