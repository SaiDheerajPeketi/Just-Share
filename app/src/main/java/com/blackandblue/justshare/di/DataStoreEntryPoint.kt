package com.blackandblue.justshare.di

import com.blackandblue.justshare.data.UserPreferencesDataStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DataStoreEntryPoint {
    fun userPreferencesDataStore(): UserPreferencesDataStore
}
