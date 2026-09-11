package com.poliku.polygoplus.di;

import android.content.Context;

import androidx.room.Room;

import com.poliku.polygoplus.data.local.PolyGoDatabase;
import com.poliku.polygoplus.data.local.dao.BlockDao;
import com.poliku.polygoplus.data.local.dao.ChatDao;
import com.poliku.polygoplus.data.local.dao.ListingDao;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public final class DatabaseModule {

    @Provides
    @Singleton
    public PolyGoDatabase provideDatabase(@ApplicationContext Context context) {
        return Room.databaseBuilder(context, PolyGoDatabase.class, "polygo.db")
                .fallbackToDestructiveMigration()
                .build();
    }

    @Provides
    public ListingDao provideListingDao(PolyGoDatabase db) {
        return db.listingDao();
    }

    @Provides
    public ChatDao provideChatDao(PolyGoDatabase db) {
        return db.chatDao();
    }

    @Provides
    public BlockDao provideBlockDao(PolyGoDatabase db) {
        return db.blockDao();
    }
}
