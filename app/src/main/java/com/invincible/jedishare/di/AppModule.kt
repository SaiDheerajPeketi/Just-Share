package com.invincible.jedishare.di

import android.content.ContentResolver
import android.content.Context
import com.invincible.jedishare.data.chat.AndroidBluetoothController
import com.invincible.jedishare.data.repository.FileTransferRepository
import com.invincible.jedishare.data.repository.MediaRepository
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

    @Provides
    @Singleton
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver {
        return context.contentResolver
    }

    @Provides
    @Singleton
    fun provideBluetoothController(@ApplicationContext context: Context): BluetoothController {
        return AndroidBluetoothController(context)
    }

    @Provides
    @Singleton
    fun provideMediaRepository(contentResolver: ContentResolver): MediaRepository {
        return MediaRepository(contentResolver)
    }

    @Provides
    @Singleton
    fun provideFileTransferRepository(contentResolver: ContentResolver): FileTransferRepository {
        return FileTransferRepository(contentResolver)
    }
}