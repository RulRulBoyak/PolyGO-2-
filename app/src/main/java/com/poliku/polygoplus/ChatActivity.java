package com.poliku.polygoplus;

import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.ChatMessageAdapter;
import com.poliku.polygoplus.network.NetworkApi;

import org.json.JSONArray;
import org.json.JSONObject;

public class ChatActivity extends AppCompatActivity {
    public static final String EXTRA_THREAD_ID = "thread_id";
    public static final String EXTRA_LISTING_ID = "listing_id";
    public static final String EXTRA_SELLER_ID = "seller_id";
    public static final String EXTRA_OTHER_NAME = "other_name";

    private String threadId, listingId, sellerId, otherName;
    private RecyclerView messageList;
    private ChatMessageAdapter adapter;
    private EditText input;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        threadId = getIntent().getStringExtra(EXTRA_THREAD_ID);
        listingId = getIntent().getStringExtra(EXTRA_LISTING_ID);
        sellerId = getIntent().getStringExtra(EXTRA_SELLER_ID);
        otherName = getIntent().getStringExtra(EXTRA_OTHER_NAME);

        if ((threadId == null || threadId.trim().isEmpty()) && (listingId == null || sellerId == null)) {
            finish();
            return;
        }

        if (otherName != null) {
            ((TextView) findViewById(R.id.chatTitle)).setText(otherName);
        }

        messageList = findViewById(R.id.chatMessages);
        messageList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatMessageAdapter(AppDataStore.userName(this));
        messageList.setAdapter(adapter);
        input = findViewById(R.id.etMessage);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.chat_main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSend).setOnClickListener(v -> sendMessage());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); return true; }
            return false;
        });
        
        loadMessages();
    }

    @Override protected void onResume() {
        super.onResume();
        loadMessages();
    }

    private void loadMessages() {
        if (threadId == null || threadId.isEmpty()) return;
        
        String userId = AppDataStore.userId(this);
        NetworkApi.getMessages(userId, threadId, new NetworkApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                JSONArray msgs = response.optJSONArray("messages");
                if (msgs != null) {
                    adapter.submit(msgs);
                    if (adapter.getItemCount() > 0) messageList.scrollToPosition(adapter.getItemCount() - 1);
                }
            }

            @Override
            public void onError(String message) {
                // Fallback to local
                AppDataStore.ThreadRecord thread = AppDataStore.getThread(ChatActivity.this, threadId);
                if (thread != null) {
                    adapter.submit(thread.messages);
                    if (adapter.getItemCount() > 0) messageList.scrollToPosition(adapter.getItemCount() - 1);
                }
            }
        });
    }

    private void sendMessage() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) { input.setError("Write a message"); return; }
        
        String userId = AppDataStore.userId(this);
        NetworkApi.sendMessage(userId, threadId, listingId, sellerId, text, new NetworkApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                input.setText("");
                if (threadId == null || threadId.isEmpty()) {
                    threadId = response.optString("thread_id");
                }
                loadMessages();
            }

            @Override
            public void onError(String message) {
                // Local fallback
                if (threadId != null) {
                    AppDataStore.sendMessage(ChatActivity.this, threadId, text);
                    input.setText("");
                    loadMessages();
                } else {
                    Toast.makeText(ChatActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void render() {
        // This is no longer used but I'll keep it as a stub if needed
    }
}
