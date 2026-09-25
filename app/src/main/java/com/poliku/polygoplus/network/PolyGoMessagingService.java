package com.poliku.polygoplus.network;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.poliku.polygoplus.HomeActivity;
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
        // FCM registration tokens are credentials. Never write them to logcat,
        // including debug builds that may be captured in shared bug reports.
        Log.d(TAG, "FCM registration token refreshed");

        // Always remember the latest token locally so a login right after a
        // rotation can still sync a valid value to the backend.
        AppDataStore.saveFcmToken(getApplicationContext(), token);

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
        String channelId = NotificationChannels.forType(data.get("type"));
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        // You could add data to the intent to open a specific screen (e.g. Chat)
        if (data.containsKey("thread_id")) {
            intent.putExtra("thread_id", data.get("thread_id"));
        }

        int requestCode = messageId == null ? title.hashCode() : messageId.hashCode();
        PendingIntent pendingIntent = PendingIntent.getActivity(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setColor(getResources().getColor(R.color.airbnb_coral, null))
                        .setContentTitle(title)
                        .setContentText(messageBody)
                        .setAutoCancel(true)
                        .setCategory(NotificationChannels.MESSAGES.equals(channelId)
                                ? NotificationCompat.CATEGORY_MESSAGE
                                : NotificationCompat.CATEGORY_STATUS)
                        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                        .setPriority(NotificationChannels.priorityFor(channelId))
                        .setContentIntent(pendingIntent);

        if (NotificationChannels.MESSAGES.equals(channelId)) {
            Person sender = new Person.Builder().setName(title).build();
            Person user = new Person.Builder().setName(getString(R.string.app_name)).build();
            notificationBuilder.setStyle(new NotificationCompat.MessagingStyle(user)
                    .setConversationTitle(title)
                    .addMessage(messageBody, System.currentTimeMillis(), sender));
        } else {
            notificationBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(messageBody));
        }

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Use message id as stable notification id: repeated id replaces the
        // previous message instead of stacking duplicates.
        int notificationId = messageId == null ? 0 : Math.abs(messageId.hashCode());

        notificationManager.notify(notificationId, notificationBuilder.build());
    }
}
