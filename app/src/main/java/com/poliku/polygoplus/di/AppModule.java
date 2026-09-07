package com.poliku.polygoplus.di;

import android.content.Context;
import android.content.SharedPreferences;

import com.poliku.polygoplus.data.AppDataStore;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public final class AppModule {

    @Provides
    @Singleton
    public SharedPreferences provideSharedPreferences(@ApplicationContext Context context) {
        return context.getSharedPreferences("polygo_local_store", Context.MODE_PRIVATE);
    }
}
