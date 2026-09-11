package com.poliku.polygoplus;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import com.google.android.material.color.DynamicColors;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.network.NetworkErrorHandler;

import androidx.work.Configuration;
import androidx.work.WorkManager;

import javax.inject.Inject;

import androidx.hilt.work.HiltWorkerFactory;
import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class PolyGoApplication extends Application implements Configuration.Provider {

    public static final String CHANNEL_ID = "polygo_updates";

    @Inject
    HiltWorkerFactory workerFactory;

    @Override
    public void onCreate() {
        super.onCreate();
        
        // Material You: Apply dynamic colors based on user wallpaper (Android 12+)
        DynamicColors.applyToActivitiesIfAvailable(this);
        
        // Initialize Core Services early
        AppDataStore.initialize(this);

        // Global network-failure -> ErrorStateActivity hook
        NetworkErrorHandler.register(this);
        
        createNotificationChannel();

        installAppCheck();
    }

    private void installAppCheck() {
        FirebaseApp.initializeApp(this);
        FirebaseAppCheck appCheck = FirebaseAppCheck.getInstance();
        if (BuildConfig.DEBUG) {
            appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance());
        } else {
            appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Campus Updates";
            String description = "Get notified about campus listings, messages, and order updates.";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder().setWorkerFactory(workerFactory).build();
    }
}
