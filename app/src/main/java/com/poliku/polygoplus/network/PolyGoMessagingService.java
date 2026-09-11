package com.poliku.polygoplus.network;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.poliku.polygoplus.HomeActivity;
import com.poliku.polygoplus.PolyGoApplication;
import com.poliku.polygoplus.R;
import com.poliku.polygoplus.api.model.BaseResponse;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;

import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class PolyGoMessagingService extends FirebaseMessagingService {
    @Inject PolyGoRepository polyGoRepository;
    private static final String TAG = "PolyGoMessaging";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        // Support both data-only (server) and notification (legacy) payloads.
        String title = null;
        String body = null;
        String messageId = null;
        Map<String, String> data = remoteMessage.getData();

        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            body = remoteMessage.getNotification().getBody();
        }
        if (data.containsKey("title") && data.containsKey("body")) {
            title = data.get("title");
            body = data.get("body");
        }
        messageId = data.get("message_id");

        if (title == null || body == null) {
            Log.d(TAG, "Notification missing title/body, ignoring.");
            return;
        }

        // Persist in-app so it survives after the tray notification is cleared.
        AppDataStore.addNotification(getApplicationContext(), title, body);
        sendNotification(title, body, messageId, data);
    }

    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "Refreshed token: " + token);
        
        // If user is logged in, sync this token to the server immediately
        String userId = AppDataStore.userId(getApplicationContext());
        if (!userId.equals("0")) {
            polyGoRepository.updateFcmToken(userId, token, new Callback<BaseResponse>() {
                @Override
                public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                    Log.d(TAG, "FCM Token synced to backend.");
                }

                @Override
                public void onFailure(Call<BaseResponse> call, Throwable t) {
                    Log.e(TAG, "FCM Sync Error: " + t.getMessage());
                }
            });
        }
    }

    private void sendNotification(String title, String messageBody, String messageId, Map<String, String> data) {
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
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setColor(getResources().getColor(R.color.airbnb_coral, null))
                        .setContentTitle(title)
                        .setContentText(messageBody)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Use message id as stable notification id: repeated id replaces the
        // previous message instead of stacking duplicates.
        int notificationId = messageId == null ? 0 : Math.abs(messageId.hashCode());

        notificationManager.notify(notificationId, notificationBuilder.build());
    }
}