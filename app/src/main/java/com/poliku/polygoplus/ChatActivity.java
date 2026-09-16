package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
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
    private boolean sending;
    private String lastMeetupLandmark = "PKS Library";
    private ActivityResultLauncher<Intent> safeMeetupLauncher;
    private boolean isBlocked;
    private boolean imeOpen;
    private boolean quickRepliesAnswered;
    private Call<PolyGoApi.SendMessageResponse> pendingSend;

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

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.chat_main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, Math.max(systemBars.bottom, ime.bottom));
            imeOpen = ime.bottom > 0;
            updateQuickChips();
            return insets;
        });

        buildQuickChips();
        updateQuickChips();

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
                    updateQuickChips();
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.MessagesResponse> call, Throwable t) {
                // Fallback to local
                AppDataStore.ThreadRecord thread = AppDataStore.getThread(ChatActivity.this, threadId);
                if (thread != null) {
                    adapter.submit(thread.messages);
                    scrollToBottom();
                    updateQuickChips();
                }
            }
        });
    }

    private void sendMessage() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            input.setError(getString(R.string.chat_error_write_message));
            return;
        }
        sendText(text);
    }

    private void sendText(String text) {
        if (sending) return;
        sending = true;
        findViewById(R.id.btnSend).setEnabled(false);
        input.setText("");

        String userId = AppDataStore.userId(this);
        pendingSend = polyGoRepository.createSendMessageCall(userId, threadId, listingId, sellerId, text);
        pendingSend.enqueue(new Callback<PolyGoApi.SendMessageResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.SendMessageResponse> call, Response<PolyGoApi.SendMessageResponse> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    // First message of a new conversation: capture the real server
                    // thread id so later messages/loads target the same thread.
                    String serverThreadId = response.body().threadId;
                    if (serverThreadId != null && !serverThreadId.trim().isEmpty()) {
                        threadId = serverThreadId;
                        AppDataStore.rememberThread(ChatActivity.this, threadId, listingId, otherName);
                    }
                    sending = false;
                    findViewById(R.id.btnSend).setEnabled(true);
                    loadMessages();
                } else {
                    onFailure(call, new Throwable("Send failed"));
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.SendMessageResponse> call, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                sending = false;
                findViewById(R.id.btnSend).setEnabled(true);
                Toast.makeText(ChatActivity.this, getString(R.string.toast_chat_network_error, t.getMessage()), Toast.LENGTH_SHORT).show();
                if (threadId != null && !threadId.isEmpty()) {
                    AppDataStore.sendMessage(ChatActivity.this, threadId, text);
                    showLocalMessages();
                }
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
        input.setHint(isBlocked ? getString(R.string.blocked_chat_hint) : getString(R.string.chat_message_hint));
        findViewById(R.id.btnSend).setEnabled(!isBlocked);
        updateQuickChips();
    }

    private void buildQuickChips() {
        ChipGroup group = findViewById(R.id.chipGroupQuickActions);
        if (group == null) return;
        String[] texts = {
                getString(R.string.quick_reply_available),
                getString(R.string.quick_reply_negotiable),
                getString(R.string.quick_reply_meetup),
                getString(R.string.quick_reply_payment)
        };
        for (String t : texts) {
            Chip chip = new Chip(this);
            chip.setText(t);
            chip.setCheckable(false);
            chip.setClickable(true);
            chip.setEnsureMinTouchTargetSize(false);
            chip.setOnClickListener(v -> {
                HapticManager.lightTap(v);
                useQuickChip(t);
            });
            group.addView(chip);
        }
    }

    private void useQuickChip(String text) {
        quickRepliesAnswered = true;
        View container = findViewById(R.id.quickChipContainer);
        if (container != null) container.setVisibility(View.GONE);
        sendText(text);
    }

    private void updateQuickChips() {
        View container = findViewById(R.id.quickChipContainer);
        if (container == null) return;
        boolean visible = !isBlocked && !imeOpen && !quickRepliesAnswered && adapter.getItemCount() < 3;
        container.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void showMoreMenu() {
        String name = otherName == null ? "Campus seller" : otherName;
        ArrayList<String> options = new ArrayList<>();
        options.add(isBlocked ? getString(R.string.menu_unblock_user) : getString(R.string.menu_block_user));
        options.add(getString(R.string.menu_report_user));
        new MaterialAlertDialogBuilder(this)
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
        new MaterialAlertDialogBuilder(this)
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
        new MaterialAlertDialogBuilder(this)
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

    private void scrollToBottom() {
        messageList.post(() -> {
            if (adapter != null && adapter.getItemCount() > 0) {
                messageList.smoothScrollToPosition(adapter.getItemCount() - 1);
            }
        });
    }

    @Override protected void onStop() {
        super.onStop();
    }

    @Override protected void onDestroy() {
        if (pendingSend != null) pendingSend.cancel();
        super.onDestroy();
    }

    private void render() {
        // This is no longer used but I'll keep it as a stub if needed
    }
}
