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
        
        String userId = AppDataStore.userId(this);
        NetworkApi.sendMessage(userId, threadId, listingId, sellerId, text, new NetworkApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                String remoteId = response.optString("thread_id");
                if (remoteId != null && !remoteId.isEmpty()) threadId = remoteId;
                sending = false;
                findViewById(R.id.btnSend).setEnabled(true);
                loadMessages();

                // Rule 3.3: Interactive Demo - Simulate seller response
                showTypingAndReply(text);
            }

            @Override
            public void onError(String message) {
                sending = false;
                findViewById(R.id.btnSend).setEnabled(true);
                Toast.makeText(ChatActivity.this, "Network error: " + message, Toast.LENGTH_SHORT).show();
                
                // Even on error, show local message and trigger reply for demo feel
                AppDataStore.sendMessage(ChatActivity.this, threadId, text);
                showLocalMessages();
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
            // Scroll to bottom so typing indicator is visible
            if (adapter.getItemCount() > 0) messageList.scrollToPosition(adapter.getItemCount() - 1);
        }
        
        // Rule 3.1: Variability - Realistic typing delay (3-6 seconds)
        long delay = 3000 + (long)(Math.random() * 3000);
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (tvTyping != null) tvTyping.setVisibility(View.GONE);
            
            String reply = getAutoReply(userMessage);
            lastAutoReply = reply;
            if (threadId != null) {
                AppDataStore.addReplyToThread(ChatActivity.this, threadId, otherName, reply);
                showLocalMessages();
            }
        }, delay);
    }

    private String getAutoReply(String userMessage) {
        String msg = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT).trim();
        String reply;

        if (containsPhrase(msg, "available", "ada lagi", "still have", "in stock", "sold out", "habis")) {
            String[] options = {
                "Yes, it's still available! Are you a student or staff? I'm usually at the Library area.",
                "It's still here! A few people messaged me but nobody confirmed yet. Want to see it tomorrow?",
                "Available! I can bring it to Block A Cafeteria later if you're interested."
            };
            reply = options[(int) (Math.random() * options.length)];
        } else if (containsPhrase(msg, "price", "berapa", "cheap", "discount", "kurang", "murah", "offer", "nego")) {
            reply = "I can give a small student discount if you pick it up at the Student Centre. How about RM 5 less?";
        } else if (containsPhrase(msg, "meet", "meetup", "where", "jumpa", "lokasi", "location", "library", "cafeteria", "block")) {
            reply = "We can meet at the PKS Library or Block B between 1pm and 2pm tomorrow. Does that work for you?";
        } else if (containsPhrase(msg, "condition", "rosak", "problem", "used", "quality", "original")) {
            reply = "It's in great condition! Used it for one semester only. No major scratches or issues.";
        } else if (containsPhrase(msg, "thank", "thanks", "terima kasih", "tq")) {
            reply = "You're welcome! Let me know if you want to proceed with the deal. 🤝";
        } else if (isGreeting(msg)) {
            reply = "Walaikumussalam! Hi, I'm at the campus now. Are you interested in the item?";
        } else {
            reply = "Got it. I'm usually around the Main Hall or Cafeteria if you want to meetup and check the item.";
        }

        if (reply.equals(lastAutoReply)) {
            reply = "Let me know if you want to set a time to meet up at PKS! I'm free after my lecture.";
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
