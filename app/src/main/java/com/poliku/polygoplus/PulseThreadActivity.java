package com.poliku.polygoplus;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.imageview.ShapeableImageView;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.ui.BaseActivity;
import com.poliku.polygoplus.ui.HapticManager;
import com.poliku.polygoplus.ui.RelativeTimeFormatter;
import com.poliku.polygoplus.ui.UiUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class PulseThreadActivity extends BaseActivity {
    private static final String EXTRA_PREFIX = "pulse_";
    private static final String EXTRA_FOCUS_COMMENT = "focus_comment";

    @Inject PolyGoRepository polyGoRepository;

    private final List<PolyGoApi.PulseComment> comments = new ArrayList<>();
    private PolyGoApi.PulseAlert post;
    private CommentAdapter adapter;
    private EditText commentInput;
    private boolean likePending;
    private boolean commentPending;

    public static Intent intent(Context context, PolyGoApi.PulseAlert item, boolean focusComment) {
        return new Intent(context, PulseThreadActivity.class)
                .putExtra(EXTRA_PREFIX + "id", item.id)
                .putExtra(EXTRA_PREFIX + "title", item.title)
                .putExtra(EXTRA_PREFIX + "body", item.body)
                .putExtra(EXTRA_PREFIX + "tag", item.tag)
                .putExtra(EXTRA_PREFIX + "user_id", item.userId)
                .putExtra(EXTRA_PREFIX + "author", item.userName)
                .putExtra(EXTRA_PREFIX + "avatar", item.profilePicUrl)
                .putExtra(EXTRA_PREFIX + "created", item.createdAt)
                .putExtra(EXTRA_PREFIX + "likes", item.likeCount)
                .putExtra(EXTRA_PREFIX + "comments", item.commentCount)
                .putExtra(EXTRA_PREFIX + "liked", item.likedByMe)
                .putExtra(EXTRA_FOCUS_COMMENT, focusComment);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pulse_thread);
        post = readPost();
        if (post.id == null || post.id.isEmpty()) {
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        commentInput = findViewById(R.id.etComment);

        RecyclerView recycler = findViewById(R.id.rvComments);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CommentAdapter();
        recycler.setAdapter(adapter);

        bindPost();
        bindComposer();
        loadComments();

        if (getIntent().getBooleanExtra(EXTRA_FOCUS_COMMENT, false)) {
            commentInput.postDelayed(this::focusComment, 250);
        }
    }

    private PolyGoApi.PulseAlert readPost() {
        PolyGoApi.PulseAlert item = new PolyGoApi.PulseAlert();
        item.id = getIntent().getStringExtra(EXTRA_PREFIX + "id");
        item.title = getIntent().getStringExtra(EXTRA_PREFIX + "title");
        item.body = getIntent().getStringExtra(EXTRA_PREFIX + "body");
        item.tag = getIntent().getStringExtra(EXTRA_PREFIX + "tag");
        item.userId = getIntent().getStringExtra(EXTRA_PREFIX + "user_id");
        item.userName = getIntent().getStringExtra(EXTRA_PREFIX + "author");
        item.profilePicUrl = getIntent().getStringExtra(EXTRA_PREFIX + "avatar");
        item.createdAt = getIntent().getLongExtra(EXTRA_PREFIX + "created", 0);
        item.likeCount = getIntent().getIntExtra(EXTRA_PREFIX + "likes", 0);
        item.commentCount = getIntent().getIntExtra(EXTRA_PREFIX + "comments", 0);
        item.likedByMe = getIntent().getBooleanExtra(EXTRA_PREFIX + "liked", false);
        return item;
    }

    private void bindPost() {
        ((TextView) findViewById(R.id.tvPulseAuthor)).setText(
                post.userName == null || post.userName.trim().isEmpty()
                        ? getString(R.string.pulse_member) : post.userName);
        String tag = post.tag == null ? "REQUEST" : post.tag.toUpperCase(Locale.ROOT);
        TextView tagView = findViewById(R.id.tvPulseTag);
        tagView.setText(tag);
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
        tagView.setTextColor(getColor(tagText));
        tagView.setBackgroundTintList(ColorStateList.valueOf(getColor(tagBackground)));
        ((TextView) findViewById(R.id.tvPulseTitle)).setText(post.title);
        TextView body = findViewById(R.id.tvPulseBody);
        body.setMaxLines(Integer.MAX_VALUE);
        body.setText(post.body);
        ((TextView) findViewById(R.id.tvPulseTime)).setText(
                RelativeTimeFormatter.format(this, post.createdAt * 1000L));
        updateCounts();
        Glide.with(this).load(post.profilePicUrl).circleCrop()
                .placeholder(R.drawable.ic_user_line).error(R.drawable.ic_user_line)
                .into((ShapeableImageView) findViewById(R.id.ivPulseAvatar));

        findViewById(R.id.threadPost).setClickable(false);
        findViewById(R.id.btnPulseComment).setOnClickListener(v -> focusComment());
        findViewById(R.id.btnPulseShare).setOnClickListener(v -> sharePost());
        findViewById(R.id.btnPulseLike).setOnClickListener(v -> setLiked());
    }

    private void bindComposer() {
        Glide.with(this).load(AppDataStore.userProfilePic(this)).circleCrop()
                .placeholder(R.drawable.ic_user_line).error(R.drawable.ic_user_line)
                .into((ShapeableImageView) findViewById(R.id.ivComposerAvatar));
        findViewById(R.id.btnSendComment).setOnClickListener(v -> postComment());
        commentInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                postComment();
                return true;
            }
            return false;
        });
    }

    private void updateCounts() {
        TextView like = findViewById(R.id.btnPulseLike);
        like.setText(post.likeCount == 0 ? "" : String.valueOf(post.likeCount));
        like.setTextColor(getColor(post.likedByMe ? R.color.favorite_coral : R.color.airbnb_muted));
        like.setCompoundDrawablesWithIntrinsicBounds(
                post.likedByMe ? R.drawable.ic_pulse_heart_filled : R.drawable.ic_pulse_heart_outline, 0, 0, 0);
        ((TextView) findViewById(R.id.btnPulseComment)).setText(
                post.commentCount == 0 ? "" : String.valueOf(post.commentCount));
    }

    private void setLiked() {
        if (!AppDataStore.isLoggedIn(this)) {
            UiUtils.snackbarError(findViewById(android.R.id.content), R.string.pulse_login_to_interact);
            return;
        }
        if (likePending) return;
        likePending = true;
        findViewById(R.id.btnPulseLike).setEnabled(false);
        boolean old = post.likedByMe;
        int oldCount = post.likeCount;
        post.likedByMe = !old;
        post.likeCount = Math.max(0, oldCount + (post.likedByMe ? 1 : -1));
        HapticManager.selectionTick(this);
        updateCounts();
        polyGoRepository.setPulseLiked(post.id, post.likedByMe, new Callback<PolyGoApi.PulseActionResponse>() {
            @Override public void onResponse(Call<PolyGoApi.PulseActionResponse> call,
                                             Response<PolyGoApi.PulseActionResponse> response) {
                PolyGoApi.PulseActionResponse result = response.body();
                likePending = false;
                findViewById(R.id.btnPulseLike).setEnabled(true);
                if (response.isSuccessful() && result != null && result.isSuccess()) {
                    post.likedByMe = result.liked;
                    post.likeCount = result.likeCount;
                } else {
                    post.likedByMe = old;
                    post.likeCount = oldCount;
                    UiUtils.snackbarError(findViewById(android.R.id.content), R.string.pulse_interaction_failed);
                }
                updateCounts();
            }
            @Override public void onFailure(Call<PolyGoApi.PulseActionResponse> call, Throwable t) {
                likePending = false;
                findViewById(R.id.btnPulseLike).setEnabled(true);
                post.likedByMe = old;
                post.likeCount = oldCount;
                updateCounts();
                UiUtils.snackbarError(findViewById(android.R.id.content), R.string.pulse_interaction_failed);
            }
        });
    }

    private void loadComments() {
        polyGoRepository.getPulseComments(post.id, new Callback<PolyGoApi.PulseCommentsResponse>() {
            @Override public void onResponse(Call<PolyGoApi.PulseCommentsResponse> call,
                                             Response<PolyGoApi.PulseCommentsResponse> response) {
                PolyGoApi.PulseCommentsResponse result = response.body();
                if (!response.isSuccessful() || result == null || !result.isSuccess()) return;
                int previousCount = comments.size();
                comments.clear();
                if (result.comments != null) comments.addAll(result.comments);
                post.commentCount = comments.size();
                if (previousCount > 0) adapter.notifyItemRangeRemoved(0, previousCount);
                if (!comments.isEmpty()) adapter.notifyItemRangeInserted(0, comments.size());
                findViewById(R.id.tvCommentsEmpty).setVisibility(comments.isEmpty() ? View.VISIBLE : View.GONE);
                updateCounts();
            }
            @Override public void onFailure(Call<PolyGoApi.PulseCommentsResponse> call, Throwable t) {
                UiUtils.snackbarError(findViewById(android.R.id.content), R.string.pulse_comments_failed);
            }
        });
    }

    private void postComment() {
        if (!AppDataStore.isLoggedIn(this)) {
            UiUtils.snackbarError(findViewById(android.R.id.content), R.string.pulse_login_to_interact);
            return;
        }
        if (commentPending) return;
        String text = commentInput.getText().toString().trim();
        if (text.isEmpty()) return;
        View send = findViewById(R.id.btnSendComment);
        commentPending = true;
        send.setEnabled(false);
        polyGoRepository.postPulseComment(post.id, text, new Callback<PolyGoApi.PulseCommentResponse>() {
            @Override public void onResponse(Call<PolyGoApi.PulseCommentResponse> call,
                                             Response<PolyGoApi.PulseCommentResponse> response) {
                commentPending = false;
                send.setEnabled(true);
                PolyGoApi.PulseCommentResponse result = response.body();
                if (response.isSuccessful() && result != null && result.isSuccess() && result.comment != null) {
                    comments.add(result.comment);
                    post.commentCount = result.commentCount;
                    adapter.notifyItemInserted(comments.size() - 1);
                    findViewById(R.id.tvCommentsEmpty).setVisibility(View.GONE);
                    commentInput.setText("");
                    updateCounts();
                    HapticManager.success(PulseThreadActivity.this);
                } else {
                    UiUtils.snackbarError(findViewById(android.R.id.content),
                            result == null ? getString(R.string.pulse_comment_failed) : result.getMessage());
                }
            }
            @Override public void onFailure(Call<PolyGoApi.PulseCommentResponse> call, Throwable t) {
                commentPending = false;
                send.setEnabled(true);
                UiUtils.snackbarError(findViewById(android.R.id.content), R.string.pulse_comment_failed);
            }
        });
    }

    private void focusComment() {
        commentInput.requestFocus();
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.showSoftInput(commentInput, InputMethodManager.SHOW_IMPLICIT);
    }

    private void sharePost() {
        Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, post.title);
        share.putExtra(Intent.EXTRA_TEXT, post.title + "\n\n" + post.body + "\n\n" +
                getString(R.string.pulse_shared_from));
        startActivity(Intent.createChooser(share, getString(R.string.pulse_share)));
    }

    private final class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.Holder> {
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_pulse_comment, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            PolyGoApi.PulseComment comment = comments.get(position);
            holder.author.setText(comment.userName);
            holder.body.setText(comment.body);
            holder.time.setText(RelativeTimeFormatter.format(PulseThreadActivity.this, comment.createdAt * 1000L));
            holder.authorBadge.setVisibility(post.userId != null && post.userId.equals(comment.userId)
                    ? View.VISIBLE : View.GONE);
            holder.connector.setVisibility(position == comments.size() - 1 ? View.INVISIBLE : View.VISIBLE);
            holder.reply.setOnClickListener(v -> {
                String name = comment.userName == null ? "" : comment.userName.trim();
                commentInput.setText(name.isEmpty() ? "" : name + ", ");
                commentInput.setSelection(commentInput.length());
                focusComment();
            });
            Glide.with(holder.itemView).load(comment.profilePicUrl).circleCrop()
                    .placeholder(R.drawable.ic_user_line).error(R.drawable.ic_user_line).into(holder.avatar);
        }
        @Override
        public int getItemCount() {
            return comments.size();
        }
        final class Holder extends RecyclerView.ViewHolder {
            final ShapeableImageView avatar;
            final View connector;
            final TextView author, authorBadge, body, time, reply;
            Holder(View view) {
                super(view);
                avatar = view.findViewById(R.id.ivCommentAvatar);
                connector = view.findViewById(R.id.commentConnector);
                author = view.findViewById(R.id.tvCommentAuthor);
                authorBadge = view.findViewById(R.id.tvCommentAuthorBadge);
                body = view.findViewById(R.id.tvCommentBody);
                time = view.findViewById(R.id.tvCommentTime);
                reply = view.findViewById(R.id.btnCommentReply);
            }
        }
    }
}
