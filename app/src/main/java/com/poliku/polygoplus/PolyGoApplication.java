package com.poliku.polygoplus;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import com.google.android.material.color.DynamicColors;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.network.NetworkApi;

import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class PolyGoApplication extends Application {

    public static final String CHANNEL_ID = "polygo_updates";

    @Override
    public void onCreate() {
        super.onCreate();
        
        // Material You: Apply dynamic colors based on user wallpaper (Android 12+)
        DynamicColors.applyToActivitiesIfAvailable(this);
        
        // Initialize Core Services early
        AppDataStore.initialize(this);
        NetworkApi.init(this);
        
        createNotificationChannel();
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
}
