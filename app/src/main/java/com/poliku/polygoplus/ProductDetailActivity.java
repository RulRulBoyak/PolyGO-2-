package com.poliku.polygoplus;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.imageview.ShapeableImageView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.api.model.BaseResponse;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.data.ProductCardAdapter;
import com.poliku.polygoplus.data.local.entity.ListingEntity;
import com.poliku.polygoplus.network.CampusMapHelper;
import com.poliku.polygoplus.ui.CarouselAdapter;
import com.poliku.polygoplus.ui.BaseActivity;
import com.poliku.polygoplus.ui.HapticManager;
import com.poliku.polygoplus.ui.RelativeTimeFormatter;
import com.poliku.polygoplus.ui.UiUtils;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;

@AndroidEntryPoint
public class ProductDetailActivity extends BaseActivity {
    @Inject PolyGoRepository polyGoRepository;
    public static final String EXTRA_LISTING_ID = "listing_id";
    public static final String RESULT_EXTRA_LISTING_ID = "result_listing_id_changed";
    private AppDataStore.ProductRecord product;
    private MaterialButton saveButton;
    private boolean favoriteChanged;
    private ViewPager2 carousel;
    private LinearLayout layoutIndicators;
    private CollapsingToolbarLayout collapsingToolbar;
    private String detailFreeSlots;
    private String detailMajorName;
    private Call<PolyGoApi.ListingsResponse> pendingFetch;
    private AppBarLayout.OnOffsetChangedListener offsetChangedListener;
    private MapView mapView;
    private MaterialButton alertButton;
    private MaterialButton followButton;
    private TextInputEditText quickMessageInput;
    private boolean isAlerted;
    private boolean isFollowing;
    private int followerCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        setContentView(R.layout.activity_product_detail);

        carousel = findViewById(R.id.productCarousel);
        ViewCompat.setTransitionName(carousel, "product_image_hero");

        View productBottomBar = findViewById(R.id.productBottomBar);
        if (productBottomBar != null) {
            ViewCompat.setOnApplyWindowInsetsListener(productBottomBar, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, v.getPaddingTop(), systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        LinearLayout mainBody = findViewById(R.id.mainBody);
        if (productBottomBar != null && mainBody != null) {
            productBottomBar.post(() -> {
                float density = getResources().getDisplayMetrics().density;
                int bottom = productBottomBar.getHeight() + productBottomBar.getPaddingBottom()
                        + Math.round(24 * density);
                mainBody.setPadding(mainBody.getPaddingLeft(), mainBody.getPaddingTop(),
                        mainBody.getPaddingRight(), bottom);
            });
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        AppBarLayout topBar = findViewById(R.id.topBar);
        if (topBar != null) {
            WindowInsetsControllerCompat insetsController =
                    WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            Drawable navIcon = toolbar != null ? toolbar.getNavigationIcon() : null;
            offsetChangedListener = (bar, verticalOffset) -> {
                boolean collapsed = bar.getTotalScrollRange() > 0
                        && Math.abs(verticalOffset) >= bar.getTotalScrollRange();
                if (navIcon != null) {
                    navIcon.setTint(collapsed ? getColor(R.color.airbnb_ink) : Color.WHITE);
                }
                insetsController.setAppearanceLightStatusBars(collapsed);
            };
            topBar.addOnOffsetChangedListener(offsetChangedListener);
        }

        String id = getIntent().getStringExtra(EXTRA_LISTING_ID);
        
        // DEEP LINKING: Check if activity was started by a URL
        Uri data = getIntent().getData();
        if (data != null && data.getPath() != null && data.getPath().startsWith("/listing/")) {
            id = data.getLastPathSegment();
        }

        if (id == null || id.isEmpty()) {
            finish();
            return;
        }

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        collapsingToolbar = findViewById(R.id.collapsingToolbar);
        if (collapsingToolbar != null) {
            // Expanded overlay title is invisible over the hero; only the collapsed bar shows the title
            collapsingToolbar.setExpandedTitleTextColor(ColorStateList.valueOf(Color.TRANSPARENT));
            collapsingToolbar.setCollapsedTitleTextColor(getColor(R.color.airbnb_ink));
        }

        saveButton = findViewById(R.id.btnSave);

        initMeetupMap(savedInstanceState);

        fetchProduct(id);
    }

    private void initMeetupMap(Bundle savedInstanceState) {
        FrameLayout container = findViewById(R.id.mapMeetupContainer);
        if (container == null) return;
        try {
            mapView = new MapView(this, new GoogleMapOptions().liteMode(true).mapType(GoogleMap.MAP_TYPE_NORMAL));
            container.addView(mapView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            mapView.onCreate(savedInstanceState);
            mapView.getMapAsync(this::configureLiteMap);
        } catch (Exception ignored) {
            // Maps provider unavailable (e.g. missing key) — the card simply renders without a map.
        }
    }

    private void configureLiteMap(GoogleMap map) {
        if (map == null) return;
        String landmark = product != null && product.location != null ? product.location : "Near campus";
        LatLng center = CampusMapHelper.getCoordinates(landmark);
        map.setIndoorEnabled(false);
        map.getUiSettings().setAllGesturesEnabled(false);
        map.getUiSettings().setMapToolbarEnabled(false);
        map.addMarker(new MarkerOptions().position(center).title(landmark));
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(center, 15f));
    }

    private void fetchProduct(String id) {
        pendingFetch = polyGoRepository.createGetListingCall(id);
        pendingFetch.enqueue(new Callback<PolyGoApi.ListingsResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.ListingsResponse> call, Response<PolyGoApi.ListingsResponse> response) {
                if (!response.isSuccessful()) {
                    tryLocalFallback(id);
                    return;
                }
                PolyGoApi.ListingsResponse body = response.body();
                if (body != null && body.listings != null && !body.listings.isEmpty()) {
                    PolyGoApi.Listing l = body.listings.get(0);
                    product = AppDataStore.ProductRecord.fromListing(l, AppDataStore.userId(ProductDetailActivity.this));
                    detailFreeSlots = l.free_slots;
                    detailMajorName = l.major_name;
                    if (product != null) {
                        renderProduct();
                        loadSimilar(id);
                    } else {
                        tryLocalFallback(id);
                    }
                } else {
                    tryLocalFallback(id);
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.ListingsResponse> call, Throwable t) {
                tryLocalFallback(id);
            }
        });
    }

    private void tryLocalFallback(String id) {
        product = AppDataStore.getListing(this, id);
        if (product != null) renderProduct();
        else {
            Toast.makeText(this, R.string.toast_product_not_found, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void renderProduct() {
        layoutIndicators = findViewById(R.id.layoutIndicators);
        List<String> images = product.imageList();
        CarouselAdapter adapter = new CarouselAdapter(product.imageRes != 0 ? product.imageRes : R.drawable.bg_product_home, position -> {
            Intent i = new Intent(this, ImageGalleryActivity.class);
            i.putStringArrayListExtra(ImageGalleryActivity.EXTRA_IMAGES, new ArrayList<>(images));
            i.putExtra(ImageGalleryActivity.EXTRA_INDEX, position);
            i.putExtra(ImageGalleryActivity.EXTRA_FALLBACK_RES, product.imageRes);
            startActivity(i);
        });
        carousel.setAdapter(adapter);
        adapter.submit(images);

        setupIndicators(images.size());
        carousel.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateIndicators(position);
            }
        });

        ((TextView) findViewById(R.id.productTitle)).setText(product.title);
        if (collapsingToolbar != null) collapsingToolbar.setTitle(product.title);
        ((TextView) findViewById(R.id.productPrice)).setText("RM " + product.price);

        TextView origPrice = findViewById(R.id.productOriginalPrice);
        if (product.originalPrice != null && !product.originalPrice.isEmpty()) {
            origPrice.setText("RM " + product.originalPrice);
            origPrice.setPaintFlags(origPrice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            origPrice.setVisibility(View.VISIBLE);
        } else {
            origPrice.setVisibility(View.GONE);
        }

        TextView condition = findViewById(R.id.productCondition);
        if (condition != null) {
            condition.setText(product.condition != null ? product.condition : "New");
        }

        String meta = "★ " + product.rating;
        if (product.reviewCount != null && !product.reviewCount.isEmpty() && !"0".equals(product.reviewCount)) {
            meta += " (" + product.reviewCount + ")";
        }
        meta += "  •  " + product.distance;
        ((TextView) findViewById(R.id.productMeta)).setText(meta);

        String postedDetail = RelativeTimeFormatter.formatDetail(this, product.postedAt);
        TextView postedView = findViewById(R.id.productPostedMeta);
        if (postedView != null) {
            if (postedDetail.isEmpty()) {
                postedView.setVisibility(View.GONE);
            } else {
                postedView.setText(postedDetail);
            }
        }

        populateMetaChips(product);

        TextView descView = findViewById(R.id.productDescription);
        if (descView != null) descView.setText(product.description);

        StringBuilder suffix = new StringBuilder();
        if (detailMajorName != null && !detailMajorName.isEmpty()) {
            suffix.append(getString(R.string.product_department_line, detailMajorName));
        }
        if (detailFreeSlots != null && !detailFreeSlots.isEmpty()) {
            suffix.append(getString(R.string.product_free_slots_line, detailFreeSlots));
        }
        if (suffix.length() > 0 && descView != null) {
            descView.setText(descView.getText() + suffix.toString());
        }

        ((TextView) findViewById(R.id.tvReviewScore)).setText(product.rating);
        boolean hasReviews = product.reviewCount != null && !product.reviewCount.isEmpty() && !"0".equals(product.reviewCount);
        ((TextView) findViewById(R.id.tvReviewCount)).setText(hasReviews
                ? getString(R.string.product_reviews_count, product.reviewCount)
                : getString(R.string.seller_no_reviews));
        findViewById(R.id.cardReviews).setOnClickListener(v -> {
            HapticManager.lightTap(v);
            Intent i = new Intent(this, SellerProfileActivity.class);
            i.putExtra(SellerProfileActivity.EXTRA_SELLER_NAME, product.seller);
            i.putExtra(SellerProfileActivity.EXTRA_SELLER_ID, product.ownerId);
            i.putExtra(SellerProfileActivity.EXTRA_SCROLL_TO_REVIEWS, true);
            startActivity(i);
        });
        View btnWriteReview = findViewById(R.id.btnWriteReview);
        if (btnWriteReview != null) {
            btnWriteReview.setOnClickListener(v -> {
                HapticManager.lightTap(v);
                showReviewGate(product.seller);
            });
        }
        ((TextView) findViewById(R.id.sellerName)).setText(product.seller);
        findViewById(R.id.sellerName).setOnClickListener(v -> {
            Intent i = new Intent(this, SellerProfileActivity.class);
            i.putExtra(SellerProfileActivity.EXTRA_SELLER_NAME, product.seller);
            i.putExtra(SellerProfileActivity.EXTRA_SELLER_ID, product.ownerId);
            startActivity(i);
        });
        View verifiedBadge = findViewById(R.id.ivVerifiedBadge);
        if (verifiedBadge != null) {
            verifiedBadge.setVisibility(product.verified ? View.VISIBLE : View.GONE);
        }
        TextView sellerMetaView = findViewById(R.id.sellerMeta);
        if (sellerMetaView != null) {
            sellerMetaView.setText(product.verified ? R.string.seller_verified_meta : R.string.seller_student_meta);
        }
        TextView mapCaption = findViewById(R.id.tvMapCaption);
        if (mapCaption != null) {
            String location = product.location == null || product.location.trim().isEmpty()
                    ? getString(R.string.location_near_campus) : product.location.trim();
            mapCaption.setText(getString(R.string.meetup_map_caption, location));
        }
        TextView trustRating = findViewById(R.id.tvSellerRating);
        if (trustRating != null) trustRating.setText(product.rating);
        findViewById(R.id.btnReportListing).setOnClickListener(v -> {
            Intent i = new Intent(this, ReportActivity.class);
            i.putExtra(ReportActivity.EXTRA_TARGET_TYPE, "listing");
            i.putExtra(ReportActivity.EXTRA_TARGET_ID, product.id);
            i.putExtra(ReportActivity.EXTRA_TARGET_NAME, product.title);
            startActivity(i);
        });

        if (!product.owner) {
            loadSellerStats(product.ownerId, product.seller);
        }

        updateSaveButton(AppDataStore.isFavorite(this, product.id));
        saveButton.setOnClickListener(v -> {
            HapticManager.lightTap(v);
            toggleFavorite();
        });

        alertButton = findViewById(R.id.btnAlertListing);
        if (alertButton != null) {
            if (product.owner) {
                alertButton.setVisibility(View.GONE);
            } else {
                updateAlertButton();
                alertButton.setOnClickListener(v -> {
                    HapticManager.lightTap(v);
                    toggleAlert();
                });
            }
        }

        followButton = findViewById(R.id.btnFollowSeller);
        setupSellerFollow();

        MaterialButton shareBtn = findViewById(R.id.btnShareListing);
        if (shareBtn != null) {
            shareBtn.setOnClickListener(v -> {
                HapticManager.lightTap(v);
                shareListing();
            });
        }

        View composerInput = findViewById(R.id.tilQuickMessage);
        View composerSend = findViewById(R.id.btnSendQuickMessage);
        quickMessageInput = findViewById(R.id.etQuickMessage);
        if (quickMessageInput != null) {
            quickMessageInput.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendQuickMessage();
                    return true;
                }
                return false;
            });
        }
        if (composerSend != null) {
            composerSend.setOnClickListener(v -> sendQuickMessage());
        }

        if (product.owner) {
            if (composerInput != null) composerInput.setVisibility(View.GONE);
            if (composerSend != null) composerSend.setVisibility(View.GONE);
            View offer = findViewById(R.id.btnMakeOffer);
            if (offer != null) offer.setVisibility(View.GONE);
            MaterialButton sold = findViewById(R.id.btnMarkSold);
            sold.setVisibility(View.VISIBLE);
            if (product.archived) {
                sold.setText(getString(R.string.product_relist));
                sold.setOnClickListener(v -> {
                    HapticManager.mediumTap(v);
                    v.setEnabled(false);
                    polyGoRepository.relistListing(product.id, new Callback<BaseResponse>() {
                        @Override
                        public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                            if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                                AppDataStore.unarchiveListing(ProductDetailActivity.this, product.id);
                                Toast.makeText(ProductDetailActivity.this, R.string.toast_listing_relisted, Toast.LENGTH_SHORT).show();
                                favoriteChanged = true; // Trigger refresh on Home
                                finish();
                            } else {
                                v.setEnabled(true);
                                Toast.makeText(ProductDetailActivity.this, R.string.toast_could_not_relist, Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<BaseResponse> call, Throwable t) {
                            v.setEnabled(true);
                            Toast.makeText(ProductDetailActivity.this, R.string.toast_could_not_reach_server, Toast.LENGTH_SHORT).show();
                        }
                    });
                });
            } else {
                sold.setEnabled(product.available);
                sold.setOnClickListener(v -> {
                    HapticManager.mediumTap(v);
                    v.setEnabled(false);
                    polyGoRepository.markSold(product.id, new Callback<BaseResponse>() {
                        @Override
                        public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                            if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                                AppDataStore.markSold(ProductDetailActivity.this, product.id);
                                Toast.makeText(ProductDetailActivity.this, R.string.toast_listing_marked_sold, Toast.LENGTH_SHORT).show();
                                favoriteChanged = true; // Trigger refresh on Home
                                finish();
                            } else {
                                v.setEnabled(true);
                                Toast.makeText(ProductDetailActivity.this, R.string.toast_could_not_mark_sold, Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<BaseResponse> call, Throwable t) {
                            v.setEnabled(true);
                            Toast.makeText(ProductDetailActivity.this, R.string.toast_could_not_reach_server, Toast.LENGTH_SHORT).show();
                        }
                    });
                });
            }
        }

        findViewById(R.id.btnMakeOffer).setOnClickListener(v -> {
            HapticManager.lightTap(v);
            if (!AppDataStore.isLoggedIn(this)) {
                Toast.makeText(this, R.string.toast_login_to_make_offer, Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, LoginActivity.class));
                return;
            }
            showOfferDialog();
        });
    }

    private void populateMetaChips(AppDataStore.ProductRecord p) {
        ChipGroup group = findViewById(R.id.chipGroupMeta);
        if (group == null) return;
        group.removeAllViews();

        String category = p.category == null ? "" : p.category.trim();
        if (!category.isEmpty()) {
            addMetaChip(group, category, UiUtils.categoryIcon(category), UiUtils.categoryColor(category));
        }
        if (detailMajorName != null && !detailMajorName.trim().isEmpty()) {
            addMetaChip(group, detailMajorName.trim(), R.drawable.ic_school, 0);
        }
        String location = p.distance == null ? "" : p.distance.trim();
        if (location.isEmpty() && p.location != null) location = p.location.trim();
        if (!location.isEmpty() && !location.equalsIgnoreCase(category)) {
            addMetaChip(group, location, R.drawable.ic_location, 0);
        }
        if (p.views > 0) {
            addMetaChip(group, getString(R.string.product_views_count, p.views), R.drawable.ic_trending_up, 0);
        }
    }

    private void addMetaChip(ChipGroup group, String text, int iconRes, int colorRes) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_info_chip, group, false);
        chip.setId(View.generateViewId());
        if (iconRes != 0) chip.setChipIcon(getDrawable(iconRes));
        if (colorRes != 0) chip.setChipIconTint(ColorStateList.valueOf(getColor(colorRes)));
        chip.setText(text);
        group.addView(chip);
    }

    private void setupSellerFollow() {
        if (followButton == null) return;
        if (product.owner) {
            followButton.setVisibility(View.GONE);
            return;
        }
        followButton.setVisibility(View.VISIBLE);
        applyFollowVisual();
        followButton.setOnClickListener(v -> {
            if (!AppDataStore.isLoggedIn(this)) {
                Toast.makeText(this, R.string.toast_login_required_save, Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, LoginActivity.class));
                return;
            }
            int sellerId;
            try {
                sellerId = Integer.parseInt(product.ownerId.trim());
            } catch (NumberFormatException e) {
                UiUtils.snackbarError(findViewById(android.R.id.content), R.string.toast_could_not_reach_server);
                return;
            }
            boolean desired = !isFollowing;
            isFollowing = desired;
            applyFollowVisual();
            polyGoRepository.followUser(AppDataStore.userId(this), sellerId, desired, new Callback<PolyGoApi.FollowResponse>() {
                @Override
                public void onResponse(Call<PolyGoApi.FollowResponse> call, Response<PolyGoApi.FollowResponse> response) {
                    PolyGoApi.FollowResponse body = response.body();
                    if (response.isSuccessful() && body != null && body.isSuccess()) {
                        followerCount = body.followerCount;
                        isFollowing = body.following;
                        applyFollowVisual();
                        TextView meta = findViewById(R.id.sellerMeta);
                        if (meta != null) updateSellerMetaWithFollowers(meta);
                    } else {
                        isFollowing = !desired;
                        applyFollowVisual();
                    }
                }

                @Override
                public void onFailure(Call<PolyGoApi.FollowResponse> call, Throwable t) {
                    isFollowing = !desired;
                    applyFollowVisual();
                    UiUtils.snackbarError(findViewById(android.R.id.content), R.string.toast_could_not_reach_server);
                }
            });
        });
    }

    private void updateSellerMetaWithFollowers(TextView metaView) {
        String base = product.verified ? getString(R.string.seller_verified_meta) : getString(R.string.seller_student_meta);
        if (followerCount > 0) {
            metaView.setText(base + "  •  " + getString(R.string.seller_followers_count, followerCount));
        } else {
            metaView.setText(base);
        }
    }

    private void applyFollowVisual() {
        if (followButton == null) return;
        if (isFollowing) {
            followButton.setText(getString(R.string.seller_following));
            followButton.setIconResource(R.drawable.ic_heart_filled);
            followButton.setIconTint(ColorStateList.valueOf(getResources().getColor(R.color.pks_blue, getTheme())));
            followButton.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.soft_blue, getTheme())));
            followButton.setTextColor(getResources().getColor(R.color.pks_blue, getTheme()));
            followButton.setStrokeColor(ColorStateList.valueOf(getResources().getColor(R.color.pks_blue, getTheme())));
            followButton.setStrokeWidth(2);
        } else {
            followButton.setText(getString(R.string.seller_follow));
            followButton.setIconResource(R.drawable.ic_heart_outline);
            followButton.setIconTint(ColorStateList.valueOf(getResources().getColor(R.color.pks_blue, getTheme())));
            followButton.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.pks_blue, getTheme())));
            followButton.setTextColor(ColorStateList.valueOf(Color.WHITE));
            followButton.setStrokeColor(ColorStateList.valueOf(Color.TRANSPARENT));
            followButton.setStrokeWidth(0);
        }
    }

    private void toggleAlert() {
        if (product == null) return;
        if (!AppDataStore.isLoggedIn(this)) {
            Toast.makeText(this, R.string.toast_login_required_save, Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        boolean desired = !isAlerted;
        isAlerted = desired;
        updateAlertButton();
        polyGoRepository.toggleAlert(AppDataStore.userId(this), product.id, new Callback<BaseResponse>() {
            @Override
            public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                if (!response.isSuccessful() || response.body() == null || !response.body().isSuccess()) {
                    isAlerted = !desired;
                    updateAlertButton();
                    return;
                }
                if (desired) {
                    Toast.makeText(ProductDetailActivity.this, R.string.toast_alert_on, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<BaseResponse> call, Throwable t) {
                isAlerted = !desired;
                updateAlertButton();
                UiUtils.snackbarError(findViewById(android.R.id.content), R.string.toast_could_not_reach_server);
            }
        });
    }

    private void updateAlertButton() {
        if (alertButton == null) return;
        alertButton.setSelected(isAlerted);
        alertButton.setContentDescription(getString(isAlerted ? R.string.product_alert_on : R.string.product_alert));
    }

    private void sendQuickMessage() {
        if (product == null) return;
        if (!AppDataStore.isLoggedIn(this)) {
            Toast.makeText(this, R.string.toast_login_to_contact, Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        String typed = quickMessageInput == null ? "" : quickMessageInput.getText().toString().trim();
        String text = typed.isEmpty() ? getString(R.string.quick_message_default) : typed;

        quickMessageInput.setEnabled(false);
        findViewById(R.id.btnSendQuickMessage).setEnabled(false);
        polyGoRepository.createSendMessageCall(AppDataStore.userId(this), null, product.id, product.ownerId, text)
                .enqueue(new Callback<PolyGoApi.SendMessageResponse>() {
                    @Override
                    public void onResponse(Call<PolyGoApi.SendMessageResponse> call, Response<PolyGoApi.SendMessageResponse> response) {
                        PolyGoApi.SendMessageResponse body = response.body();
                        if (response.isSuccessful() && body != null && body.isSuccess() && body.threadId != null) {
                            HapticManager.success(ProductDetailActivity.this);
                            AppDataStore.rememberThread(ProductDetailActivity.this, body.threadId, product.id, product.seller);
                            openThread(body.threadId);
                        } else {
                            resetComposer();
                            UiUtils.snackbarError(findViewById(android.R.id.content), R.string.toast_message_failed);
                        }
                    }

                    @Override
                    public void onFailure(Call<PolyGoApi.SendMessageResponse> call, Throwable t) {
                        resetComposer();
                        UiUtils.snackbarError(findViewById(android.R.id.content), R.string.toast_could_not_reach_server);
                    }
                });
    }

    private void resetComposer() {
        if (quickMessageInput != null) quickMessageInput.setEnabled(true);
        View send = findViewById(R.id.btnSendQuickMessage);
        if (send != null) send.setEnabled(true);
    }

    private void openThread(String threadId) {
        Intent i = new Intent(this, ChatActivity.class);
        i.putExtra(ChatActivity.EXTRA_THREAD_ID, threadId);
        i.putExtra(ChatActivity.EXTRA_LISTING_ID, product.id);
        i.putExtra(ChatActivity.EXTRA_SELLER_ID, product.ownerId);
        i.putExtra(ChatActivity.EXTRA_OTHER_NAME, product.seller);
        startActivity(i);
    }

    private void shareListing() {
        String url = "https://polygo.pks.edu.my/listing/" + product.id;
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, product.title);
        send.putExtra(Intent.EXTRA_TEXT, product.title + "\n" + url);
        startActivity(Intent.createChooser(send, getString(R.string.share_listing_label)));
    }

    private void loadSimilar(String id) {
        LinearLayout section = findViewById(R.id.similarSection);
        RecyclerView rv = findViewById(R.id.rvSimilar);
        if (section == null || rv == null) return;
        rv.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        ProductCardAdapter adapter = new ProductCardAdapter(null, (a, product, view) -> {
            Intent i = new Intent(this, ProductDetailActivity.class);
            i.putExtra(EXTRA_LISTING_ID, product.id);
            startActivity(i);
        });
        adapter.setItemWidthDp(170);
        rv.setAdapter(adapter);
        polyGoRepository.createSimilarCall(id).enqueue(new Callback<PolyGoApi.ListingsResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.ListingsResponse> call, Response<PolyGoApi.ListingsResponse> response) {
                if (isFinishing() || isDestroyed()) return;
                if (!response.isSuccessful()) {
                    section.setVisibility(View.GONE);
                    return;
                }
                List<ListingEntity> items = new ArrayList<>();
                PolyGoApi.ListingsResponse body = response.body();
                if (body != null && body.listings != null) {
                    String currentUserId = AppDataStore.userId(ProductDetailActivity.this);
                    for (PolyGoApi.Listing l : body.listings) {
                        items.add(new ListingEntity(l.id, l.title, l.seller, l.price, String.valueOf(l.rating),
                                l.distance != null ? l.distance : (l.location == null ? "" : l.location),
                                l.image_url, l.category, l.description, l.owner_id, l.available,
                                currentUserId != null && currentUserId.equals(l.owner_id),
                                l.location, l.postedAt, l.views));
                    }
                }
                adapter.updateData(items);
                section.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onFailure(Call<PolyGoApi.ListingsResponse> call, Throwable t) {
                section.setVisibility(View.GONE);
            }
        });
    }

    private void loadSellerStats(String ownerId, String sellerName) {
        if (ownerId == null || ownerId.trim().isEmpty()) return;
        polyGoRepository.getSeller(ownerId, sellerName, new Callback<PolyGoApi.SellerResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.SellerResponse> call, Response<PolyGoApi.SellerResponse> response) {
                if (isFinishing() || isDestroyed()) return;
                if (!response.isSuccessful()) return;
                PolyGoApi.SellerResponse body = response.body();
                if (body == null || body.user == null || body.user.isPrivate) return;

                TextView meta = findViewById(R.id.sellerMeta);
                if (meta == null) return;
                PolyGoApi.User seller = body.user;
                isFollowing = seller.following;
                followerCount = seller.followerCount;
                if (followButton != null) {
                    applyFollowVisual();
                }
                updateSellerMetaWithFollowers(meta);
                String pic = seller.profile_pic_url == null ? "" : seller.profile_pic_url.trim();
                if (!pic.isEmpty()) {
                    ShapeableImageView avatar = findViewById(R.id.sellerAvatar);
                    if (avatar != null) {
                        Glide.with(ProductDetailActivity.this)
                                .load(pic)
                                .placeholder(R.drawable.ic_person)
                                .error(R.drawable.ic_person)
                                .override(96, 96)
                                .circleCrop()
                                .into(avatar);
                    }
                }

                TextView soldView = findViewById(R.id.tvSellerSold);
                if (soldView != null) soldView.setText(String.valueOf(seller.sold));
                TextView joinedView = findViewById(R.id.tvSellerJoined);
                if (joinedView != null && seller.joined_at != null && !seller.joined_at.trim().isEmpty()) {
                    joinedView.setText(formatJoined(seller.joined_at));
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.SellerResponse> call, Throwable t) {
            }
        });
    }

    private String formatJoined(String raw) {
        try {
            return new SimpleDateFormat("MMM yyyy", Locale.ENGLISH)
                    .format(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).parse(raw.trim()));
        } catch (Exception ignored) {
            return raw;
        }
    }

    private void setupIndicators(int count) {
        layoutIndicators.removeAllViews();
        if (count <= 1) return;
        for (int i = 0; i < count; i++) {
            ImageView dot = new ImageView(this);
            dot.setImageResource(R.drawable.dot_inactive);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(24, 24);
            params.setMargins(8, 0, 8, 0);
            layoutIndicators.addView(dot, params);
        }
        updateIndicators(0);
    }

    private void updateIndicators(int position) {
        for (int i = 0; i < layoutIndicators.getChildCount(); i++) {
            ImageView dot = (ImageView) layoutIndicators.getChildAt(i);
            dot.setImageResource(i == position ? R.drawable.dot_active : R.drawable.dot_inactive);
        }
    }

    private void updateSaveButton(boolean saved) {
        saveButton.setSelected(saved);
        saveButton.setText(saved ? R.string.product_saved : R.string.product_save);
        saveButton.setIconResource(saved ? R.drawable.ic_star_filled : R.drawable.ic_star_outline);
    }

    private void toggleFavorite() {
        if (!AppDataStore.isLoggedIn(this)) {
            Toast.makeText(this, R.string.toast_login_required_save, Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        // Optimistic UI: flip state immediately, roll back only if the call fails.
        AppDataStore.toggleFavorite(this, product.id);
        boolean isFav = AppDataStore.isFavorite(this, product.id);
        updateSaveButton(isFav);
        favoriteChanged = true;

        String userId = AppDataStore.userId(this);
        polyGoRepository.toggleFavorite(userId, product.id, new Callback<BaseResponse>() {
            @Override
            public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    boolean fav = AppDataStore.isFavorite(ProductDetailActivity.this, product.id);
                    updateSaveButton(fav);

                    if (!fav) {
                        Snackbar.make(saveButton, R.string.snack_removed_from_saved, Snackbar.LENGTH_LONG)
                                .setAction(getString(R.string.action_undo), v -> toggleFavorite())
                                .setActionTextColor(getResources().getColor(R.color.pks_blue_variant))
                                .show();
                    } else {
                        Toast.makeText(ProductDetailActivity.this, R.string.toast_saved_to_items, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    onFailure(call, new Throwable("Toggle failed"));
                }
            }

            @Override
            public void onFailure(Call<BaseResponse> call, Throwable t) {
                // Roll back the optimistic toggle and keep the local state consistent.
                AppDataStore.toggleFavorite(ProductDetailActivity.this, product.id);
                boolean fav = AppDataStore.isFavorite(ProductDetailActivity.this, product.id);
                updateSaveButton(fav);

                Toast.makeText(ProductDetailActivity.this, R.string.toast_favorite_update_failed,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showOfferDialog() {
        OfferSheetDialogFragment.newInstance(product.id, product.ownerId, product.title, product.price, product.seller)
                .show(getSupportFragmentManager(), "offer");
    }

    private void showReviewGate(String sellerName) {
        if (!AppDataStore.isLoggedIn(this)) {
            Toast.makeText(this, R.string.toast_login_to_leave_review, Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
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
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.review_verified_buyer_title)
                .setMessage(R.string.review_verified_buyer_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    protected void onPause() {
        if (mapView != null) mapView.onPause();
        super.onPause();
    }

    @Override
    public void onLowMemory() {
        if (mapView != null) mapView.onLowMemory();
        super.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        if (pendingFetch != null) pendingFetch.cancel();
        if (mapView != null) mapView.onDestroy();
        AppBarLayout topBar = findViewById(R.id.topBar);
        if (topBar != null && offsetChangedListener != null) {
            topBar.removeOnOffsetChangedListener(offsetChangedListener);
        }
        offsetChangedListener = null;
        super.onDestroy();
    }

    @Override
    public void finish() {
        if (favoriteChanged && product != null) {
            Intent result = new Intent();
            result.putExtra(RESULT_EXTRA_LISTING_ID, product.id);
            setResult(RESULT_OK, result);
        }
        super.finish();
    }
}
