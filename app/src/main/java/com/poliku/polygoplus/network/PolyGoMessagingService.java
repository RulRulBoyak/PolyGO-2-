package com.poliku.polygoplus.network;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.poliku.polygoplus.HomeActivity;
import com.poliku.polygoplus.PolyGoApplication;
import com.poliku.polygoplus.R;
import com.poliku.polygoplus.data.AppDataStore;

import org.json.JSONObject;

public class PolyGoMessagingService extends FirebaseMessagingService {
    private static final String TAG = "PolyGoMessaging";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        // Check if message contains a notification payload.
        if (remoteMessage.getNotification() != null) {
            String title = remoteMessage.getNotification().getTitle();
            String body = remoteMessage.getNotification().getBody();
            sendNotification(title, body, remoteMessage.getData());
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "Refreshed token: " + token);
        
        // If user is logged in, sync this token to the server immediately
        String userId = AppDataStore.userId(getApplicationContext());
        if (!userId.equals("0")) {
            NetworkApi.updateFcmToken(userId, token, new NetworkApi.Callback() {
                @Override public void onSuccess(JSONObject response) { Log.d(TAG, "FCM Token synced to backend."); }
                @Override public void onError(String message) { Log.e(TAG, "FCM Sync Error: " + message); }
            });
        }
    }

    private void sendNotification(String title, String messageBody, java.util.Map<String, String> data) {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        // You could add data to the intent to open a specific screen (e.g. Chat)
        if (data.containsKey("thread_id")) {
            intent.putExtra("thread_id", data.get("thread_id"));
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, PolyGoApplication.CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_nav_explore) // Replace with your app icon
                        .setContentTitle(title)
                        .setContentText(messageBody)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        notificationManager.notify(0, notificationBuilder.build());
    }
}
