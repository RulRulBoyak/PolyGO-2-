package com.poliku.polygoplus;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import android.content.Intent;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.bumptech.glide.Glide;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.ui.BaseActivity;
import com.poliku.polygoplus.ui.HapticManager;
import com.poliku.polygoplus.ui.RelativeTimeFormatter;
import com.poliku.polygoplus.ui.UiUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class CampusPulseActivity extends BaseActivity {

    @Inject PolyGoRepository polyGoRepository;

    private final List<PolyGoApi.PulseAlert> items = new ArrayList<>();
    private final Set<String> pendingLikes = new HashSet<>();
    private PulseAdapter adapter;
    private TextView tvPulseEmpty;
    private ActivityResultLauncher<Intent> createPulseLauncher;
    private boolean loadingPulse;
    private boolean loadErrorShown;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshFeed = new Runnable() {
        @Override public void run() {
            refreshPulse();
            refreshHandler.postDelayed(this, 15000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_campus_pulse);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        tvPulseEmpty = findViewById(R.id.tvPulseEmpty);

        createPulseLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                refreshPulse();
            }
        });

        RecyclerView rv = findViewById(R.id.rvPulse);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PulseAdapter(items);
        rv.setAdapter(adapter);

        findViewById(R.id.btnPostRequest).setOnClickListener(v -> {
            HapticManager.swell(this);
            Intent intent = new Intent(this, CreatePulseActivity.class);
            createPulseLauncher.launch(intent);
            overridePendingTransition(R.anim.slide_in_up, R.anim.fade_out);
        });

    }

    private void refreshPulse() {
        if (loadingPulse) return;
        loadingPulse = true;
        polyGoRepository.getPulse(new Callback<PolyGoApi.PulseResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.PulseResponse> call, Response<PolyGoApi.PulseResponse> response) {
                loadingPulse = false;
                PolyGoApi.PulseResponse body = response.body();
                if (response.isSuccessful() && body != null && body.alerts != null) {
                    loadErrorShown = false;
                    int previousItemCount = items.size();
                    items.clear();
                    items.addAll(body.alerts);
                    if (body.announcements != null) items.addAll(body.announcements);
                    items.sort((left, right) -> Long.compare(right.createdAt, left.createdAt));
                    if (previousItemCount > 0) adapter.notifyItemRangeRemoved(0, previousItemCount);
                    if (!items.isEmpty()) adapter.notifyItemRangeInserted(0, items.size());
                    tvPulseEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                } else {
                    showLoadErrorOnce();
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.PulseResponse> call, Throwable t) {
                loadingPulse = false;
                showLoadErrorOnce();
            }
        });
    }

    private void showLoadErrorOnce() {
        if (loadErrorShown) return;
        loadErrorShown = true;
        UiUtils.snackbarError(findViewById(android.R.id.content), R.string.pulse_load_failed);
    }

    @Override protected void onResume() {
        super.onResume();
        refreshHandler.removeCallbacks(refreshFeed);
        refreshFeed.run();
    }

    @Override protected void onPause() {
        refreshHandler.removeCallbacks(refreshFeed);
        super.onPause();
    }

    @Override protected void onDestroy() {
        refreshHandler.removeCallbacks(refreshFeed);
        super.onDestroy();
    }



    private class PulseAdapter extends RecyclerView.Adapter<PulseAdapter.Holder> {
        private final List<PolyGoApi.PulseAlert> items;

        PulseAdapter(List<PolyGoApi.PulseAlert> i) {
            items = i;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pulse_alert, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            PolyGoApi.PulseAlert item = items.get(position);
            String tag = item.tag == null ? "REQUEST" : item.tag.toUpperCase(Locale.ROOT);
            h.tag.setText(tag);
            int tagText = R.color.pks_blue;
            int tagBackground = R.color.surface_tint_info;
            if ("REQUEST".equals(tag)) {
                tagText = R.color.cat_food;
                tagBackground = R.color.soft_food;
            } else if ("FLASH SALE".equals(tag)) {
                tagText = R.color.cat_fashion;
                tagBackground = R.color.soft_fashion;
            } else if ("EVENT".equals(tag)) {
                tagText = R.color.pks_green;
                tagBackground = R.color.soft_green;
            }
            h.tag.setTextColor(getColor(tagText));
            h.tag.setBackgroundTintList(ColorStateList.valueOf(getColor(tagBackground)));
            h.author.setText(item.userName == null || item.userName.trim().isEmpty()
                    ? getString(R.string.pulse_member) : item.userName);
            h.title.setText(item.title);
            h.body.setText(item.body);
            h.time.setText(RelativeTimeFormatter.format(h.itemView.getContext(), item.createdAt * 1000L));
            h.comments.setText(item.commentCount == 0 ? "" : String.valueOf(item.commentCount));
            h.like.setText(item.likeCount == 0 ? "" : String.valueOf(item.likeCount));
            h.like.setCompoundDrawablesWithIntrinsicBounds(
                    item.likedByMe ? R.drawable.ic_pulse_heart_filled : R.drawable.ic_pulse_heart_outline, 0, 0, 0);
            h.like.setTextColor(getColor(item.likedByMe ? R.color.favorite_coral : R.color.airbnb_muted));
            h.like.setEnabled(!pendingLikes.contains(item.id));
            Glide.with(h.itemView)
                    .load(item.profilePicUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_user_line)
                    .error(R.drawable.ic_user_line)
                    .into(h.avatar);

            h.itemView.setOnClickListener(v -> openThread(item, false));
            h.comments.setOnClickListener(v -> openThread(item, true));
            h.share.setOnClickListener(v -> shareThread(item));
            h.like.setOnClickListener(v -> setLiked(item, h));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            com.google.android.material.imageview.ShapeableImageView avatar;
            TextView tag, author, title, body, time, comments, like, share;

            Holder(View v) {
                super(v);
                tag = v.findViewById(R.id.tvPulseTag);
                avatar = v.findViewById(R.id.ivPulseAvatar);
                author = v.findViewById(R.id.tvPulseAuthor);
                title = v.findViewById(R.id.tvPulseTitle);
                body = v.findViewById(R.id.tvPulseBody);
                time = v.findViewById(R.id.tvPulseTime);
                comments = v.findViewById(R.id.btnPulseComment);
                like = v.findViewById(R.id.btnPulseLike);
                share = v.findViewById(R.id.btnPulseShare);
            }
        }

        private void setLiked(PolyGoApi.PulseAlert item, Holder holder) {
            if (!AppDataStore.isLoggedIn(CampusPulseActivity.this)) {
                UiUtils.snackbarError(findViewById(android.R.id.content), R.string.pulse_login_to_interact);
                return;
            }
            if (!pendingLikes.add(item.id)) return;
            boolean oldLiked = item.likedByMe;
            int oldCount = item.likeCount;
            item.likedByMe = !oldLiked;
            item.likeCount = Math.max(0, oldCount + (item.likedByMe ? 1 : -1));
            HapticManager.selectionTick(CampusPulseActivity.this);
            int tappedPosition = holder.getBindingAdapterPosition();
            if (tappedPosition != RecyclerView.NO_POSITION) notifyItemChanged(tappedPosition);
            polyGoRepository.setPulseLiked(item.id, item.likedByMe, new Callback<PolyGoApi.PulseActionResponse>() {
                @Override public void onResponse(Call<PolyGoApi.PulseActionResponse> call,
                                                 Response<PolyGoApi.PulseActionResponse> response) {
                    pendingLikes.remove(item.id);
                    PolyGoApi.PulseActionResponse body = response.body();
                    if (response.isSuccessful() && body != null && body.isSuccess()) {
                        item.likedByMe = body.liked;
                        item.likeCount = body.likeCount;
                    } else {
                        item.likedByMe = oldLiked;
                        item.likeCount = oldCount;
                        UiUtils.snackbarError(findViewById(android.R.id.content), R.string.pulse_interaction_failed);
                    }
                    int position = items.indexOf(item);
                    if (position >= 0) notifyItemChanged(position);
                }

                @Override public void onFailure(Call<PolyGoApi.PulseActionResponse> call, Throwable t) {
                    pendingLikes.remove(item.id);
                    item.likedByMe = oldLiked;
                    item.likeCount = oldCount;
                    int position = items.indexOf(item);
                    if (position >= 0) notifyItemChanged(position);
                    UiUtils.snackbarError(findViewById(android.R.id.content), R.string.pulse_interaction_failed);
                }
            });
        }
    }

    private void openThread(PolyGoApi.PulseAlert item, boolean focusComment) {
        HapticManager.lightTap(findViewById(android.R.id.content));
        Intent intent = PulseThreadActivity.intent(this, item, focusComment);
        startActivity(intent);
    }

    private void shareThread(PolyGoApi.PulseAlert item) {
        HapticManager.lightTap(findViewById(android.R.id.content));
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, item.title);
        share.putExtra(Intent.EXTRA_TEXT, item.title + "\n\n" + item.body + "\n\n" +
                getString(R.string.pulse_shared_from));
        startActivity(Intent.createChooser(share, getString(R.string.pulse_share)));
    }

}
