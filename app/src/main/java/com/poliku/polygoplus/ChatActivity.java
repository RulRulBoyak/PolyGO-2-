package com.poliku.polygoplus;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.api.model.BaseResponse;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.ChatMessageAdapter;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.ui.HapticManager;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;

@AndroidEntryPoint
public class ChatActivity extends AppCompatActivity {
    @Inject PolyGoRepository polyGoRepository;
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
    private final Handler typingHandler = new Handler(Looper.getMainLooper());
    private Runnable hideTypingRunnable;
    private Animator typingAnimator;
    private String lastMeetupLandmark = "PKS Library";
    private ActivityResultLauncher<Intent> safeMeetupLauncher;
    private boolean isBlocked;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        safeMeetupLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null
                    && result.getData().getBooleanExtra(SafeMeetupActivity.EXTRA_ARRIVED, false)) {
                String landmark = result.getData().getStringExtra(SafeMeetupActivity.EXTRA_LANDMARK);
                if (landmark == null || landmark.trim().isEmpty()) landmark = "PKS Library";
                sendText(getString(R.string.safe_meetup_arrived, landmark));
                updateMeetupTracker();
            }
        });

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
        loadOtherAvatar();

        messageList = findViewById(R.id.chatMessages);
        messageList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatMessageAdapter(AppDataStore.userName(this));
        messageList.setAdapter(adapter);
        input = findViewById(R.id.etMessage);
        tvTyping = findViewById(R.id.tvTypingStatus);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.chat_main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, Math.max(systemBars.bottom, ime.bottom));
            return insets;
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnChatMore).setOnClickListener(v -> {
            HapticManager.lightTap(v);
            showMoreMenu();
        });
        findViewById(R.id.meetupTrackerCard).setOnClickListener(v -> openSafeMeetup());
        findViewById(R.id.btnProfileLink).setOnClickListener(v -> {
            HapticManager.lightTap(v);
            Intent i = new Intent(this, SellerProfileActivity.class);
            i.putExtra(SellerProfileActivity.EXTRA_SELLER_ID, sellerId);
            i.putExtra(SellerProfileActivity.EXTRA_SELLER_NAME, otherName == null ? "Campus seller" : otherName);
            startActivity(i);
        });
        findViewById(R.id.btnSend).setOnClickListener(v -> {
            HapticManager.lightTap(v);
            sendMessage();
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });
        
        loadMessages();
        checkBlockState();
    }

    @Override protected void onResume() {
        super.onResume();
        loadMessages();
        updateMeetupTracker();
    }

    private void loadOtherAvatar() {
        if (sellerId == null || sellerId.trim().isEmpty()) return;
        polyGoRepository.getSeller(sellerId, otherName, new Callback<PolyGoApi.SellerResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.SellerResponse> call, Response<PolyGoApi.SellerResponse> response) {
                if (isFinishing() || isDestroyed()) return;
                if (!response.isSuccessful() || response.body() == null || response.body().user == null) return;
                String pic = response.body().user.profile_pic_url;
                if (pic == null || pic.trim().isEmpty()) return;
                ImageView avatar = findViewById(R.id.ivChatAvatar);
                Glide.with(ChatActivity.this)
                        .load(pic.trim())
                        .placeholder(R.drawable.ic_user_line)
                        .error(R.drawable.ic_user_line)
                        .circleCrop()
                        .into(avatar);
            }

            @Override
            public void onFailure(Call<PolyGoApi.SellerResponse> call, Throwable t) {
            }
        });
    }

    private void loadMessages() {
        if (threadId == null || threadId.isEmpty()) return;
        
        String userId = AppDataStore.userId(this);
        polyGoRepository.getMessages(userId, threadId, new Callback<PolyGoApi.MessagesResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.MessagesResponse> call, Response<PolyGoApi.MessagesResponse> response) {
                PolyGoApi.MessagesResponse body = response.body();
                if (body != null && body.messages != null) {
                    adapter.submitMessages(body.messages);
                    scrollToBottom();
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.MessagesResponse> call, Throwable t) {
                // Fallback to local
                AppDataStore.ThreadRecord thread = AppDataStore.getThread(ChatActivity.this, threadId);
                if (thread != null) {
                    adapter.submit(thread.messages);
                    scrollToBottom();
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
        sendText(text);
    }

    private void sendText(String text) {
        if (sending) return;
        sending = true;
        findViewById(R.id.btnSend).setEnabled(false);
        input.setText("");

        if (threadId == null || threadId.trim().isEmpty()) {
            threadId = AppDataStore.ensureThread(this, listingId, otherName);
        }
        
        String userId = AppDataStore.userId(this);
        polyGoRepository.sendMessage(userId, threadId, listingId, sellerId, text, new Callback<BaseResponse>() {
            @Override
            public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    sending = false;
                    findViewById(R.id.btnSend).setEnabled(true);
                    loadMessages();

                    // Rule 3.3: Interactive Demo - Simulate seller response
                    showTypingAndReply(text);
                } else {
                    onFailure(call, new Throwable("Send failed"));
                }
            }

            @Override
            public void onFailure(Call<BaseResponse> call, Throwable t) {
                sending = false;
                findViewById(R.id.btnSend).setEnabled(true);
                Toast.makeText(ChatActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                
                // Even on error, show local message and trigger reply for demo feel
                AppDataStore.sendMessage(ChatActivity.this, threadId, text);
                showLocalMessages();
                showTypingAndReply(text);
            }
        });
    }

    private void checkBlockState() {
        if (sellerId == null || sellerId.trim().isEmpty()) return;
        String userId = AppDataStore.userId(this);
        polyGoRepository.getBlockedUsers(userId, new Callback<PolyGoApi.BlockResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.BlockResponse> call, Response<PolyGoApi.BlockResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().blockedIds != null) {
                    isBlocked = response.body().blockedIds.contains(sellerId);
                    applyBlockedState();
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.BlockResponse> call, Throwable t) {
            }
        });
    }

    private void applyBlockedState() {
        if (isFinishing() || isDestroyed()) return;
        input.setEnabled(!isBlocked);
        input.setHint(isBlocked ? getString(R.string.blocked_chat_hint) : "Message...");
        findViewById(R.id.btnSend).setEnabled(!isBlocked);
    }

    private void showMoreMenu() {
        String name = otherName == null ? "Campus seller" : otherName;
        ArrayList<String> options = new ArrayList<>();
        options.add(isBlocked ? getString(R.string.menu_unblock_user) : getString(R.string.menu_block_user));
        options.add(getString(R.string.menu_report_user));
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        if (isBlocked) {
                            confirmUnblock();
                        } else {
                            confirmBlock();
                        }
                    } else {
                        openReportUser();
                    }
                })
                .show();
    }

    private void confirmBlock() {
        String name = otherName == null ? "Campus seller" : otherName;
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.block_user_confirm_title, name))
                .setMessage(R.string.block_user_confirm_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.menu_block_user, (d, w) -> doBlock())
                .show();
    }

    private void doBlock() {
        if (sellerId == null || sellerId.trim().isEmpty()) return;
        String userId = AppDataStore.userId(this);
        polyGoRepository.blockUser(userId, sellerId, new Callback<BaseResponse>() {
            @Override
            public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    isBlocked = true;
                    applyBlockedState();
                    loadMessages();
                    Toast.makeText(ChatActivity.this, R.string.user_blocked, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ChatActivity.this, R.string.blocked_update_failed, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<BaseResponse> call, Throwable t) {
                Toast.makeText(ChatActivity.this, R.string.blocked_update_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmUnblock() {
        String name = otherName == null ? "Campus seller" : otherName;
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.unblock_user_confirm_title, name))
                .setMessage(R.string.unblock_user_confirm_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.menu_unblock_user, (d, w) -> doUnblock())
                .show();
    }

    private void doUnblock() {
        if (sellerId == null || sellerId.trim().isEmpty()) return;
        String userId = AppDataStore.userId(this);
        polyGoRepository.unblockUser(userId, sellerId, new Callback<BaseResponse>() {
            @Override
            public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    isBlocked = false;
                    applyBlockedState();
                    Toast.makeText(ChatActivity.this, R.string.user_unblocked, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ChatActivity.this, R.string.blocked_update_failed, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<BaseResponse> call, Throwable t) {
                Toast.makeText(ChatActivity.this, R.string.blocked_update_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openReportUser() {
        Intent i = new Intent(this, ReportActivity.class);
        i.putExtra(ReportActivity.EXTRA_TARGET_TYPE, "user");
        if (sellerId != null) i.putExtra(ReportActivity.EXTRA_TARGET_ID, sellerId);
        if (otherName != null) i.putExtra(ReportActivity.EXTRA_TARGET_NAME, otherName);
        startActivity(i);
    }

    private void updateMeetupTracker() {
        View card = findViewById(R.id.meetupTrackerCard);
        if (listingId == null || listingId.trim().isEmpty()) {
            card.setVisibility(View.GONE);
            return;
        }
        for (AppDataStore.TransactionRecord t : AppDataStore.getTransactions(this)) {
            if (listingId.equals(t.listingId) && AppDataStore.isActiveMeetupPhase(t.status)) {
                doShowTracker(t.location, t.status);
                return;
            }
        }
        card.setVisibility(View.GONE);
        refreshTrackerFromServer();
    }

    private void refreshTrackerFromServer() {
        String userId = AppDataStore.userId(this);
        if (userId == null || userId.isEmpty()) return;
        polyGoRepository.getTransactions(userId, new Callback<PolyGoApi.TransactionsResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.TransactionsResponse> call, Response<PolyGoApi.TransactionsResponse> response) {
                PolyGoApi.TransactionsResponse body = response.body();
                if (body == null || body.transactions == null) return;
                for (PolyGoApi.Transaction t : body.transactions) {
                    if (listingId != null && listingId.equals(t.listingId) && AppDataStore.isActiveMeetupPhase(t.status)) {
                        doShowTracker(t.location, t.status);
                        return;
                    }
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.TransactionsResponse> call, Throwable t) {
                // Fallback: keep local state
            }
        });
    }

    private void doShowTracker(String landmark, String status) {
        if (landmark == null || landmark.trim().isEmpty()) landmark = "PKS Library";
        lastMeetupLandmark = landmark;
        ((TextView) findViewById(R.id.tvMeetupLandmark)).setText(getString(R.string.chat_meetup_landmark, landmark));
        String norm = status == null ? "" : status.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        TextView statusView = findViewById(R.id.tvMeetupStatus);
        if (norm.equals("pickup")) {
            statusView.setText(getString(R.string.chat_meetup_status_pickup));
        } else {
            statusView.setText(getString(R.string.chat_meetup_status_accepted));
        }
        findViewById(R.id.meetupTrackerCard).setVisibility(View.VISIBLE);
    }

    private void openSafeMeetup() {
        HapticManager.lightTap(findViewById(R.id.meetupTrackerCard));
        Intent i = new Intent(this, SafeMeetupActivity.class);
        i.putExtra(SafeMeetupActivity.EXTRA_THREAD_ID, threadId);
        i.putExtra(SafeMeetupActivity.EXTRA_LISTING_ID, listingId);
        i.putExtra(SafeMeetupActivity.EXTRA_SELLER_ID, sellerId);
        i.putExtra(SafeMeetupActivity.EXTRA_OTHER_NAME, otherName);
        i.putExtra(SafeMeetupActivity.EXTRA_LANDMARK, lastMeetupLandmark);
        i.putExtra(SafeMeetupActivity.EXTRA_DEAL_ACTIVE, true);
        safeMeetupLauncher.launch(i);
    }

    private void showLocalMessages() {
        if (threadId == null || threadId.isEmpty()) return;
        AppDataStore.ThreadRecord thread = AppDataStore.getThread(this, threadId);
        if (thread == null) return;
        adapter.submit(thread.messages);
        scrollToBottom();
        AppDataStore.markThreadRead(this, threadId);
    }

    private void showTypingAndReply(String userMessage) {
        showTypingIndicator();

        // Rule 3.1: Variability - Realistic typing delay (3-6 seconds)
        long delay = 3000 + (long)(Math.random() * 3000);

        if (hideTypingRunnable != null) typingHandler.removeCallbacks(hideTypingRunnable);
        hideTypingRunnable = () -> {
            hideTypingIndicator();
            String reply = getAutoReply(userMessage);
            lastAutoReply = reply;
            if (threadId != null) {
                AppDataStore.addReplyToThread(ChatActivity.this, threadId, otherName, reply);
                showLocalMessages();
            }
        };
        typingHandler.postDelayed(hideTypingRunnable, delay);
    }

    private void showTypingIndicator() {
        if (tvTyping == null) return;
        tvTyping.setText(otherName + getString(R.string.chat_typing_status));
        tvTyping.setVisibility(View.VISIBLE);
        tvTyping.setAlpha(1f);
        if (typingAnimator != null) typingAnimator.cancel();
        ObjectAnimator animator = ObjectAnimator.ofFloat(tvTyping, View.ALPHA, 1f, 0.3f);
        animator.setDuration(550);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        typingAnimator = animator;
        typingAnimator.start();
        scrollToBottom();
    }

    private void hideTypingIndicator() {
        if (typingAnimator != null) {
            typingAnimator.cancel();
            typingAnimator = null;
        }
        if (tvTyping != null) {
            tvTyping.setVisibility(View.GONE);
            tvTyping.setAlpha(1f);
        }
    }

    private void scrollToBottom() {
        messageList.post(() -> {
            if (adapter != null && adapter.getItemCount() > 0) {
                messageList.smoothScrollToPosition(adapter.getItemCount() - 1);
            }
        });
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

    @Override protected void onStop() {
        super.onStop();
        typingHandler.removeCallbacks(hideTypingRunnable);
        hideTypingIndicator();
    }

    @Override protected void onDestroy() {
        typingHandler.removeCallbacks(hideTypingRunnable);
        if (typingAnimator != null) typingAnimator.cancel();
        super.onDestroy();
    }

    private void render() {
        // This is no longer used but I'll keep it as a stub if needed
    }
}
