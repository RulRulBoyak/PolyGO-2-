package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.ProductCardAdapter;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.data.local.entity.ListingEntity;
import com.poliku.polygoplus.ui.BaseActivity;
import com.poliku.polygoplus.ui.EmptyStates;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class MyListingsActivity extends BaseActivity {
    @Inject PolyGoRepository polyGoRepository;
    private ProductCardAdapter adapter;
    private View empty;
    private LinearLayout draftsContainer;
    private View draftsSection;
    private TextView draftCount;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_saved_items);
        ((TextView) findViewById(R.id.pageTitle)).setText("My listings");
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        RecyclerView rv = findViewById(R.id.rvSaved);
        rv.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new ProductCardAdapter(new ArrayList<>(), new ProductCardAdapter.Listener() {
            @Override
            public void onProduct(ProductCardAdapter adapter, ListingEntity p, View sharedView) {
                Intent i = new Intent(MyListingsActivity.this, ProductDetailActivity.class);
                i.putExtra(ProductDetailActivity.EXTRA_LISTING_ID, p.id);
                startActivity(i);
            }

            @Override
            public void onDataChanged() {
                load();
            }
        });
        rv.setAdapter(adapter);
        empty = findViewById(R.id.tvEmpty);
        draftsSection = findViewById(R.id.draftsSection);
        draftsContainer = findViewById(R.id.draftsContainer);
        draftCount = findViewById(R.id.tvDraftCount);
        EmptyStates.bind(empty, R.drawable.ic_edit_square, "You haven't published any listings",
                "Create a listing or save a draft until you are ready.",
                "Create listing", v -> startActivity(new Intent(this, EditProductActivity.class)));
        load();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        load();
    }

    private void load() {
        polyGoRepository.getMyListings(new Callback<PolyGoApi.ListingsResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.ListingsResponse> call, Response<PolyGoApi.ListingsResponse> response) {
                List<ListingEntity> items = new ArrayList<>();
                PolyGoApi.ListingsResponse body = response.body();
                if (body != null && body.listings != null) {
                    for (PolyGoApi.Listing l : body.listings) {
                        AppDataStore.ProductRecord p = AppDataStore.ProductRecord.fromListing(l, AppDataStore.userId(MyListingsActivity.this));
                        if (p != null) {
                            ListingEntity e = p.toEntity();
                            e.archived = l.archivedAt != null && !l.archivedAt.isEmpty();
                            items.add(e);
                        }
                    }
                }
                render(items);
            }

            @Override
            public void onFailure(Call<PolyGoApi.ListingsResponse> call, Throwable t) {
                render(AppDataStore.getMyListings(MyListingsActivity.this).stream()
                        .map(AppDataStore.ProductRecord::toEntity)
                        .collect(java.util.stream.Collectors.toList()));
            }
        });
    }

    private void render(List<ListingEntity> items) {
        if (adapter != null) adapter.updateData(items);
        List<JSONObject> drafts = AppDataStore.getDrafts(this);
        String title = getString(R.string.profile_my_listings);
        long archived = items.stream().filter(i -> i.archived).count();
        if (archived > 0) title += " · " + archived + " archived";
        ((TextView) findViewById(R.id.pageTitle)).setText(title);
        renderDrafts(drafts);
        empty.setVisibility(items.isEmpty() && drafts.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void renderDrafts(List<JSONObject> drafts) {
        draftsContainer.removeAllViews();
        draftsSection.setVisibility(drafts.isEmpty() ? View.GONE : View.VISIBLE);
        draftCount.setText(getString(R.string.drafts_saved_count, drafts.size()));
        for (JSONObject draft : drafts) draftsContainer.addView(createDraftCard(draft));
    }

    private View createDraftCard(JSONObject draft) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(dp(260), ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMarginEnd(dp(10));
        card.setLayoutParams(cardParams);
        card.setCardBackgroundColor(getColor(R.color.white));
        card.setRadius(dp(16));
        card.setStrokeColor(getColor(R.color.grey_200));
        card.setStrokeWidth(dp(1));
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(14), dp(12), dp(10));

        TextView title = new TextView(this);
        String name = draft.optString("title").trim();
        title.setText(name.isEmpty() ? getString(R.string.draft_untitled) : name);
        title.setTextColor(getColor(R.color.airbnb_ink));
        title.setTextSize(16);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        content.addView(title);

        TextView details = new TextView(this);
        String category = draft.optString("category").trim();
        CharSequence saved = DateUtils.getRelativeTimeSpanString(
                draft.optLong("time", System.currentTimeMillis()), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        details.setText(category.isEmpty() ? saved : category + "  ·  " + saved);
        details.setTextColor(getColor(R.color.airbnb_muted));
        details.setTextSize(13);
        details.setPadding(0, dp(4), 0, dp(6));
        content.addView(details);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        MaterialButton delete = new MaterialButton(this);
        delete.setText(R.string.draft_delete);
        delete.setTextColor(getColor(R.color.airbnb_muted));
        delete.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        delete.setMinHeight(0);
        delete.setMinimumHeight(0);
        MaterialButton resume = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        resume.setText(R.string.draft_resume);
        resume.setMinHeight(dp(40));
        actions.addView(delete);
        actions.addView(resume);
        content.addView(actions);
        card.addView(content);

        View.OnClickListener open = v -> openDraft(draft.optString("id"));
        card.setOnClickListener(open);
        resume.setOnClickListener(open);
        delete.setOnClickListener(v -> confirmDeleteDraft(draft));
        return card;
    }

    private void openDraft(String id) {
        Intent intent = new Intent(this, EditProductActivity.class);
        intent.putExtra("draft_id", id);
        startActivity(intent);
    }

    private void confirmDeleteDraft(JSONObject draft) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.draft_delete_title)
                .setMessage(R.string.draft_delete_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.draft_delete, (dialog, which) -> {
                    AppDataStore.deleteDraft(this, draft.optString("id"));
                    List<JSONObject> remaining = AppDataStore.getDrafts(this);
                    remaining.removeIf(item -> draft.optString("id").equals(item.optString("id")));
                    renderDrafts(remaining);
                })
                .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
