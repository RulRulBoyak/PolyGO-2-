package com.poliku.polygoplus;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
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
    private TextView tvTyping;

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
        tvTyping = findViewById(R.id.tvTypingStatus);

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
        input.setText("");
        
        // Simulating the user's message locally immediately for speed
        if (threadId != null) {
            AppDataStore.sendMessage(this, threadId, text);
            loadMessages();
        }

        NetworkApi.sendMessage(userId, threadId, listingId, sellerId, text, new NetworkApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (threadId == null || threadId.isEmpty()) {
                    threadId = response.optString("thread_id");
                }
                loadMessages();
                
                // Show typing status for demo
                showTypingAndReply(text);
            }

            @Override
            public void onError(String message) {
                // For demo, we always want interactivity even without network
                if (threadId == null) {
                    threadId = AppDataStore.getOrCreateThread(ChatActivity.this, listingId, otherName, text);
                } else {
                    AppDataStore.sendMessage(ChatActivity.this, threadId, text);
                }
                loadMessages();
                
                showTypingAndReply(text);
            }
        });
    }

    private void showTypingAndReply(String userMessage) {
        if (tvTyping != null) {
            tvTyping.setText(otherName + " is typing...");
            tvTyping.setVisibility(View.VISIBLE);
        }
        
        // WhatsApp-style typing delay (4-5 seconds)
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (tvTyping != null) tvTyping.setVisibility(View.GONE);
            
            String reply = getAutoReply(userMessage);
            if (threadId != null) {
                AppDataStore.addReplyToThread(ChatActivity.this, threadId, otherName, reply);
                loadMessages();
            }
        }, 4500);
    }

    private String getAutoReply(String userMessage) {
        String msg = userMessage.toLowerCase();
        
        if (msg.contains("hi") || msg.contains("hello") || msg.contains("pagi") || msg.contains("assalam")) 
            return "Walaikumussalam! Hi, I'm the seller. How can I help you today? 😊";
            
        if (msg.contains("available") || msg.contains("ada lagi") || msg.contains("still have")) 
            return "Yes, it's still available! I have a few people asking, but first come first served. Are you a student or staff?";
            
        if (msg.contains("price") || msg.contains("cheap") || msg.contains("discount") || msg.contains("kurang")) 
            return "I can give you a small student discount if you pick it up today at the Student Center! How does RM 5 less sound?";
            
        if (msg.contains("meet") || msg.contains("where") || msg.contains("jumpa") || msg.contains("pks")) 
            return "We can meet at the PKS Library or Block A Cafeteria tomorrow between 1pm to 2pm. Is that okay for you?";
            
        if (msg.contains("condition") || msg.contains("okay") || msg.contains("rosak") || msg.contains("problem")) 
            return "It's in almost perfect condition, only used for one semester. You can check it properly when we meet! 👍";

        if (msg.contains("student") || msg.contains("lecturer") || msg.contains("staff"))
            return "Great! It's good to deal with fellow PKS community members. Let me know when you want to proceed with the deal.";

        return "That sounds good! Let me check my schedule and I'll confirm the meetup time with you shortly. Anything else you'd like to know?";
    }

    private void render() {
        // This is no longer used but I'll keep it as a stub if needed
    }
}
