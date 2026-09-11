package com.poliku.polygoplus;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.api.model.BaseResponse;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.NotificationAdapter;
import com.poliku.polygoplus.data.PolyGoRepository;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class NotificationsActivity extends AppCompatActivity {
    @Inject PolyGoRepository polyGoRepository;
    private NotificationAdapter adapter;
    private View empty;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        empty=findViewById(R.id.layoutEmptyNotifications);
        com.poliku.polygoplus.ui.EmptyStates.bind(empty, R.drawable.ic_bell, "No notifications yet",
                "Updates about your listings and messages will show up here.", null, null);
        RecyclerView list=findViewById(R.id.notificationList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter=new NotificationAdapter(notification -> {
            AppDataStore.markNotificationRead(this, notification.id);
            loadNotifications();
        });
        list.setAdapter(adapter);
        loadNotifications();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) {
            loadNotifications();
        }
    }

    @Override protected void onStop() {
        super.onStop();
        AppDataStore.markNotificationsRead(this);
        String userId = AppDataStore.userId(this);
        if (userId != null && !userId.isEmpty()) {
            polyGoRepository.markNotificationsRead(userId, new Callback<BaseResponse>() {
                @Override public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                }

                @Override public void onFailure(Call<BaseResponse> call, Throwable t) {
                }
            });
        }
    }

    private void loadNotifications() {
        String userId = AppDataStore.userId(this);
        if (userId != null && !userId.isEmpty()) {
            polyGoRepository.getNotifications(userId, new Callback<PolyGoApi.NotificationsResponse>() {
                @Override
                public void onResponse(Call<PolyGoApi.NotificationsResponse> call, Response<PolyGoApi.NotificationsResponse> response) {
                    PolyGoApi.NotificationsResponse body = response.body();
                    if (body != null && body.notifications != null) {
                        AppDataStore.syncNotifications(NotificationsActivity.this, body.notifications);
                    }
                    render();
                }

                @Override
                public void onFailure(Call<PolyGoApi.NotificationsResponse> call, Throwable t) {
                    render();
                }
            });
        } else {
            render();
        }
    }

    private void render() {
        java.util.List<AppDataStore.NotificationRecord> items=AppDataStore.getNotifications(this);
        adapter.submit(items);
        empty.setVisibility(items.isEmpty()?View.VISIBLE:View.GONE);
    }
}
