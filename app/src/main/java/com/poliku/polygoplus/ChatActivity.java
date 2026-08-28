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

import java.util.Locale;
import java.util.regex.Pattern;

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
    private boolean sending;
    private String lastAutoReply = "";

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
        if (text.isEmpty()) {
            input.setError("Write a message");
            return;
        }
        if (sending) return;
        sending = true;
        findViewById(R.id.btnSend).setEnabled(false);
        input.setText("");

        if (threadId == null || threadId.trim().isEmpty()) {
            threadId = AppDataStore.ensureThread(this, listingId, otherName);
        }
        AppDataStore.sendMessage(this, threadId, text);
        showLocalMessages();

        String userId = AppDataStore.userId(this);
        NetworkApi.sendMessage(userId, threadId, listingId, sellerId, text, new NetworkApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                String remoteId = response.optString("thread_id");
                if (remoteId != null && !remoteId.isEmpty()) threadId = remoteId;
                sending = false;
                findViewById(R.id.btnSend).setEnabled(true);
                showTypingAndReply(text);
            }

            @Override
            public void onError(String message) {
                sending = false;
                findViewById(R.id.btnSend).setEnabled(true);
                showTypingAndReply(text);
            }
        });
    }

    private void showLocalMessages() {
        if (threadId == null || threadId.isEmpty()) return;
        AppDataStore.ThreadRecord thread = AppDataStore.getThread(this, threadId);
        if (thread == null) return;
        adapter.submit(thread.messages);
        if (adapter.getItemCount() > 0) messageList.scrollToPosition(adapter.getItemCount() - 1);
        AppDataStore.markThreadRead(this, threadId);
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
            lastAutoReply = reply;
            if (threadId != null) {
                AppDataStore.addReplyToThread(ChatActivity.this, threadId, otherName, reply);
                showLocalMessages();
            }
        }, 4500);
    }

    private String getAutoReply(String userMessage) {
        String msg = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT).trim();
        String reply;

        if (containsPhrase(msg, "available", "ada lagi", "still have", "in stock", "sold out", "habis")) {
            reply = "Yes, it's still available! A few people asked already, but first come first served. Are you a student or staff?";
        } else if (containsPhrase(msg, "price", "berapa", "cheap", "discount", "kurang", "murah", "offer", "nego")) {
            reply = "I can give a small student discount if you pick it up today at the Student Centre. How does RM 5 less sound?";
        } else if (containsPhrase(msg, "meet", "meetup", "where", "jumpa", "lokasi", "location", "library", "cafeteria", "block")) {
            reply = "We can meet at the PKS Library or Block A Cafeteria tomorrow between 1pm and 2pm. Does that work for you?";
        } else if (containsPhrase(msg, "condition", "rosak", "problem", "used", "quality", "original")) {
            reply = "It's in almost perfect condition, only used for one semester. You can check it properly when we meet.";
        } else if (containsPhrase(msg, "student", "lecturer", "staff")) {
            reply = "Great — always nicer dealing with fellow PKS community members. Tell me when you want to proceed.";
        } else if (containsPhrase(msg, "thank", "thanks", "terima kasih", "tq")) {
            reply = "You're welcome! Message me again if you need anything else.";
        } else if (isGreeting(msg)) {
            reply = "Walaikumussalam! Hi, I'm the seller. How can I help you today?";
        } else {
            reply = "Got it. Do you want to check availability, price, or a meetup spot at PKS?";
        }

        if (reply.equals(lastAutoReply)) {
            reply = "I already noted that. Want me to confirm if it's still available, the price, or a meetup time?";
        }
        return reply;
    }

    private boolean isGreeting(String msg) {
        if (msg.contains("assalam") || msg.contains("salamualaikum")) return true;
        return containsPhrase(msg, "hello", "hey", "hi", "pagi", "petang", "malam");
    }

    private boolean containsPhrase(String msg, String... phrases) {
        for (String phrase : phrases) {
            if (phrase.contains(" ")) {
                if (msg.contains(phrase)) return true;
            } else if (Pattern.compile("\\b" + Pattern.quote(phrase) + "\\b").matcher(msg).find()) {
                return true;
            }
        }
        return false;
    }

    private void render() {
        // This is no longer used but I'll keep it as a stub if needed
    }
}
