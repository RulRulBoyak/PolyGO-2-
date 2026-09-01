package com.poliku.polygoplus;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.NotificationAdapter;

public class NotificationsActivity extends AppCompatActivity {
    private NotificationAdapter adapter;
    private View empty;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        empty=findViewById(R.id.layoutEmptyNotifications);
        com.poliku.polygoplus.ui.EmptyStates.bind(empty, android.R.drawable.ic_popup_reminder, "No notifications yet",
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

    @Override protected void onResume() { super.onResume(); if(adapter!=null) loadNotifications(); }

    @Override protected void onStop() {
        super.onStop();
        AppDataStore.markNotificationsRead(this);
    }

    private void loadNotifications() {
        java.util.List<AppDataStore.NotificationRecord> items=AppDataStore.getNotifications(this);
        adapter.submit(items);
        empty.setVisibility(items.isEmpty()?View.VISIBLE:View.GONE);
    }
}
