package com.poliku.polygoplus;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.data.ProductCardAdapter;
import com.poliku.polygoplus.data.local.entity.ListingEntity;
import com.poliku.polygoplus.ui.EmptyStates;
import com.poliku.polygoplus.ui.HapticManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class SellerProfileActivity extends AppCompatActivity {
    public static final String EXTRA_SELLER_NAME = "seller_name";
    public static final String EXTRA_SELLER_ID = "seller_id";
    public static final String EXTRA_SCROLL_TO_REVIEWS = "scroll_to_reviews";

    @Inject PolyGoRepository polyGoRepository;

    private boolean following;
    private boolean privateMode;
    private NestedScrollView profileScroll;
    private View headerListings;
    private View headerReviews;
    private String sellerName = "Campus seller";
    private String sellerOwnerId;
    private final List<AppDataStore.ReviewRecord> reviews = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_seller_profile);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        profileScroll = findViewById(R.id.profileScroll);
        headerListings = findViewById(R.id.headerListings);
        headerReviews = findViewById(R.id.headerReviews);
        findViewById(R.id.privateCard).setVisibility(View.GONE);

        String seller = getIntent().getStringExtra(EXTRA_SELLER_NAME);
        String sellerId = getIntent().getStringExtra(EXTRA_SELLER_ID);
        if (seller == null || seller.isEmpty()) seller = "Campus seller";
        sellerName = seller;
        sellerOwnerId = sellerId;

        TextView tvName = findViewById(R.id.tvSellerName);
        tvName.setText(sellerName);

        List<ListingEntity> active = new ArrayList<>();
        for (AppDataStore.ProductRecord p : AppDataStore.getListingsBySeller(this, sellerName, sellerOwnerId)) {
            if (p.available) active.add(p.toEntity());
        }
        String firstActiveListingId = active.isEmpty() ? null : active.get(0).id;

        float rating = AppDataStore.averageRatingForSeller(this, sellerName);
        int sold = AppDataStore.countSoldBySeller(this, sellerName, sellerOwnerId);
        ((TextView) findViewById(R.id.tvSellerRating)).setText(rating <= 0 ? "New" : String.format(Locale.US, "%.1f", rating));
        ((TextView) findViewById(R.id.tvItemsSold)).setText(String.valueOf(Math.max(sold, 0)));
        ((TextView) findViewById(R.id.tvGreenImpact)).setText(getString(R.string.profile_top_15_percent));
        ((TextView) findViewById(R.id.tvListingsCount)).setText(String.valueOf(active.size()));

        RecyclerView rv = findViewById(R.id.rvSellerListings);
        rv.setLayoutManager(new GridLayoutManager(this, 2));
        rv.setAdapter(new ProductCardAdapter(active, new ProductCardAdapter.Listener() {
            @Override
            public void onProduct(ProductCardAdapter adapter, ListingEntity p, View sharedView) {
                Intent i = new Intent(SellerProfileActivity.this, ProductDetailActivity.class);
                i.putExtra(ProductDetailActivity.EXTRA_LISTING_ID, p.id);
                startActivity(i);
            }
        }));
        View emptyListings = findViewById(R.id.emptyListings);
        emptyListings.setVisibility(active.isEmpty() ? View.VISIBLE : View.GONE);
        EmptyStates.bind(emptyListings, R.drawable.ic_grid, getString(R.string.seller_no_active),
                getString(R.string.seller_no_products_desc, sellerName), getString(R.string.seller_browse_campus), v -> {
                    startActivity(new Intent(this, SearchActivity.class));
                });

        reviews.addAll(AppDataStore.getReviewsForSeller(this, sellerName));
        renderReviews();

        findViewById(R.id.btnReportUser).setOnClickListener(v -> {
            Intent i = new Intent(this, ReportActivity.class);
            i.putExtra(ReportActivity.EXTRA_TARGET_TYPE, "user");
            i.putExtra(ReportActivity.EXTRA_TARGET_ID, sellerOwnerId);
            i.putExtra(ReportActivity.EXTRA_TARGET_NAME, sellerName);
            startActivity(i);
        });

        findViewById(R.id.btnAddReview).setOnClickListener(v -> {
            HapticManager.lightTap(v);
            showReviewGate(sellerName);
        });

        setupFollowButton();
        MaterialButton btnMessage = findViewById(R.id.btnMessageSeller);
        btnMessage.setOnClickListener(v -> {
            HapticManager.lightTap(v);
            if (sellerOwnerId == null && firstActiveListingId == null) {
                Toast.makeText(this, getString(R.string.seller_no_active), Toast.LENGTH_SHORT).show();
                return;
            }
            if (!AppDataStore.isLoggedIn(this)) {
                startActivity(new Intent(this, LoginActivity.class));
                return;
            }
            Intent i = new Intent(this, ChatActivity.class);
            i.putExtra(ChatActivity.EXTRA_SELLER_ID, sellerOwnerId);
            i.putExtra(ChatActivity.EXTRA_LISTING_ID, firstActiveListingId);
            i.putExtra(ChatActivity.EXTRA_OTHER_NAME, sellerName);
            startActivity(i);
        });

        findViewById(R.id.statImpact).setOnClickListener(v -> {
            HapticManager.lightTap(v);
            startActivity(new Intent(this, SustainabilityDashboardActivity.class));
        });
        findViewById(R.id.statSold).setOnClickListener(v -> scrollToView(headerListings));
        findViewById(R.id.statRating).setOnClickListener(v -> scrollToView(headerReviews));
        headerListings.setOnClickListener(v -> scrollToView(headerListings));
        headerReviews.setOnClickListener(v -> scrollToView(headerReviews));

        loadRemoteSeller(sellerOwnerId, sellerName);
        if (getIntent().getBooleanExtra(EXTRA_SCROLL_TO_REVIEWS, false)) {
            scrollToView(headerReviews);
        }
    }

    private void setupFollowButton() {
        MaterialButton follow = findViewById(R.id.btnFollow);
        follow.setOnClickListener(v -> {
            HapticManager.lightTap(v);
            following = !following;
            if (following) {
                follow.setText(getString(R.string.seller_following));
                follow.setIconResource(R.drawable.ic_heart_filled);
                follow.setIconTint(ColorStateList.valueOf(getResources().getColor(R.color.pks_blue, getTheme())));
                follow.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.soft_blue, getTheme())));
                follow.setTextColor(getResources().getColor(R.color.pks_blue, getTheme()));
                follow.setStrokeColor(ColorStateList.valueOf(getResources().getColor(R.color.pks_blue, getTheme())));
                follow.setStrokeWidth(2);
            } else {
                follow.setText(getString(R.string.seller_follow));
                follow.setIconResource(R.drawable.ic_heart_outline);
                follow.setIconTint(ColorStateList.valueOf(Color.WHITE));
                follow.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.pks_blue, getTheme())));
                follow.setTextColor(Color.WHITE);
                follow.setStrokeColor(ColorStateList.valueOf(Color.TRANSPARENT));
                follow.setStrokeWidth(0);
            }
        });
    }

    private void scrollToView(View target) {
        profileScroll.post(() -> profileScroll.smoothScrollTo(0, Math.max(0, target.getTop())));
    }

    private void showReviewGate(String sellerName) {
        if (!AppDataStore.isLoggedIn(this)) {
            Toast.makeText(this, "Please log in to leave a review", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        if (sellerOwnerId != null && sellerOwnerId.equals(AppDataStore.userId(this))) {
            Toast.makeText(this, "You cannot review yourself", Toast.LENGTH_SHORT).show();
            return;
        }
        AppDataStore.TransactionRecord tx = AppDataStore.getEligibleReviewTransaction(this, sellerName);
        if (tx != null) {
            Intent review = new Intent(this, ReviewActivity.class);
            review.putExtra(ReviewActivity.EXTRA_SELLER, sellerName);
            review.putExtra(ReviewActivity.EXTRA_TRANSACTION_ID, tx.id);
            review.putExtra(ReviewActivity.EXTRA_LISTING_ID, tx.listingId);
            startActivity(review);
            return;
        }
        HapticManager.error(this);
        new AlertDialog.Builder(this)
                .setTitle(R.string.review_verified_buyer_title)
                .setMessage(R.string.review_verified_buyer_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void loadRemoteSeller(String sellerId, String sellerName) {
        polyGoRepository.getSeller(sellerId, sellerName, new Callback<PolyGoApi.SellerResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.SellerResponse> call, Response<PolyGoApi.SellerResponse> response) {
                if (!isFinishing() && !isDestroyed() && response.isSuccessful() && response.body() != null) {
                    PolyGoApi.User remote = response.body().user;
                    if (remote == null) return;
                    if (remote.isPrivate) {
                        showPrivateState();
                        return;
                    }
                    hidePrivateState();
                    if (remote.name != null && !remote.name.isEmpty()) {
                        ((TextView) findViewById(R.id.tvSellerName)).setText(remote.name);
                    }
                    if (remote.bio != null && !remote.bio.trim().isEmpty()) {
                        TextView bioView = findViewById(R.id.tvSellerBio);
                        bioView.setVisibility(View.VISIBLE);
                        bioView.setText(remote.bio.trim());
                    }
                    if (remote.verified) {
                        findViewById(R.id.tvSellerBadge).setVisibility(View.VISIBLE);
                        findViewById(R.id.ivVerifiedBadge).setVisibility(View.VISIBLE);
                    }
                    if (response.body().reviews != null && !response.body().reviews.isEmpty()) {
                        List<AppDataStore.ReviewRecord> server = new ArrayList<>();
                        for (PolyGoApi.SellerReview r : response.body().reviews) {
                            server.add(new AppDataStore.ReviewRecord("", sellerName,
                                    r.reviewer_name == null ? "Student" : r.reviewer_name,
                                    r.comment == null ? "" : r.comment, r.stars, 0L));
                        }
                        mergeReviews(server);
                    }
                    runOnUiThread(() -> {
                        ((TextView) findViewById(R.id.tvItemsSold)).setText(String.valueOf(Math.max(remote.sold, 0)));
                        renderReviews();
                    });
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.SellerResponse> call, Throwable t) {
                // Local data already rendered; bio/verified stay hidden.
            }
        });
    }

    private void mergeReviews(List<AppDataStore.ReviewRecord> server) {
        for (AppDataStore.ReviewRecord r : server) {
            boolean dup = false;
            for (AppDataStore.ReviewRecord local : reviews) {
                if (local.reviewer.equalsIgnoreCase(r.reviewer) && local.stars == r.stars
                        && local.comment.equals(r.comment)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                reviews.add(r);
            }
        }
    }

    private void renderReviews() {
        if (isFinishing() || isDestroyed()) return;
        LinearLayout reviewList = findViewById(R.id.reviewList);
        reviewList.removeAllViews();
        ((TextView) findViewById(R.id.tvReviewsCount)).setText(String.valueOf(reviews.size()));
        View emptyReviews = findViewById(R.id.emptyReviews);
        if (reviews.isEmpty()) {
            emptyReviews.setVisibility(View.VISIBLE);
            EmptyStates.bind(emptyReviews, R.drawable.ic_star_outline, getString(R.string.seller_no_reviews),
                    getString(R.string.seller_no_reviews_desc), null, null);
        } else {
            emptyReviews.setVisibility(View.GONE);
            for (AppDataStore.ReviewRecord review : reviews) {
                TextView row = new TextView(this);
                row.setPadding(0, 16, 0, 16);
                row.setTextColor(getResources().getColor(R.color.airbnb_ink, getTheme()));
                row.setTextSize(15);
                String stars = "★★★★★".substring(0, Math.max(1, Math.min(5, review.stars)));
                row.setText(stars + "  " + review.reviewer + "\n" + review.comment);
                reviewList.addView(row);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sellerOwnerId != null || (sellerName != null && !sellerName.isEmpty())) {
            loadRemoteSeller(sellerOwnerId, sellerName);
        }
    }

    private void showPrivateState() {
        privateMode = true;
        findViewById(R.id.privateCard).setVisibility(View.VISIBLE);
        findViewById(R.id.statsCard).setVisibility(View.GONE);
        findViewById(R.id.headerListings).setVisibility(View.GONE);
        findViewById(R.id.rvSellerListings).setVisibility(View.GONE);
        findViewById(R.id.emptyListings).setVisibility(View.GONE);
        findViewById(R.id.headerReviews).setVisibility(View.GONE);
        findViewById(R.id.reviewList).setVisibility(View.GONE);
        findViewById(R.id.emptyReviews).setVisibility(View.GONE);
        findViewById(R.id.btnFollow).setVisibility(View.GONE);
        findViewById(R.id.btnMessageSeller).setVisibility(View.GONE);
        findViewById(R.id.btnAddReview).setVisibility(View.GONE);
    }

    private void hidePrivateState() {
        boolean wasPrivate = privateMode;
        privateMode = false;
        if (!wasPrivate) return;
        findViewById(R.id.privateCard).setVisibility(View.GONE);
        findViewById(R.id.statsCard).setVisibility(View.VISIBLE);
        findViewById(R.id.headerListings).setVisibility(View.VISIBLE);
        findViewById(R.id.headerReviews).setVisibility(View.VISIBLE);
        findViewById(R.id.btnFollow).setVisibility(View.VISIBLE);
        findViewById(R.id.btnMessageSeller).setVisibility(View.VISIBLE);
        findViewById(R.id.btnAddReview).setVisibility(View.VISIBLE);
    }
}