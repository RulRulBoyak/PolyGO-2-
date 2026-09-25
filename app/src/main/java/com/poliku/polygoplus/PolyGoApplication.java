package com.poliku.polygoplus;

import android.app.Application;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.network.AuthSessionHandler;
import com.poliku.polygoplus.network.NetworkErrorHandler;
import com.poliku.polygoplus.network.NotificationChannels;

import androidx.work.Configuration;
import androidx.work.WorkManager;

import javax.inject.Inject;

import androidx.hilt.work.HiltWorkerFactory;
import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class PolyGoApplication extends Application implements Configuration.Provider {

    /** Legacy fallback for notifications sent by older server/app versions. */
    public static final String CHANNEL_ID = NotificationChannels.LEGACY;

    @Inject
    HiltWorkerFactory workerFactory;

    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize Core Services early
        AppDataStore.initialize(this);

        // Global network-failure -> ErrorStateActivity hook
        NetworkErrorHandler.register(this);

        // Session-expiry interceptor + activity foreground tracker
        AuthSessionHandler.register(this);
        
        NotificationChannels.createAll(this);

        installAppCheck();
    }

    private void installAppCheck() {
        FirebaseApp.initializeApp(this);
        FirebaseAppCheck appCheck = FirebaseAppCheck.getInstance();
        AppCheckProviderInstaller.install(appCheck);
    }

    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder().setWorkerFactory(workerFactory).build();
    }
}
