package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.imageview.ShapeableImageView;
import androidx.core.view.ViewCompat;
import androidx.viewpager2.widget.ViewPager2;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.network.NetworkApi;
import com.poliku.polygoplus.ui.CarouselAdapter;

import java.util.ArrayList;
import java.util.List;

public class ProductDetailActivity extends AppCompatActivity {
    public static final String EXTRA_LISTING_ID = "listing_id";
    private AppDataStore.ProductRecord product;
    private com.google.android.material.button.MaterialButton saveButton;
    private ViewPager2 carousel;
    private android.widget.LinearLayout layoutIndicators;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_detail);
        postponeEnterTransition();

        String id = getIntent().getStringExtra(EXTRA_LISTING_ID);
        if (id == null || id.isEmpty()) {
            finish();
            return;
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        saveButton = findViewById(R.id.btnSave);

        fetchProduct(id);
    }

    private void fetchProduct(String id) {
        NetworkApi.getListing(id, new NetworkApi.Callback() {
            @Override
            public void onSuccess(org.json.JSONObject response) {
                org.json.JSONObject data = response.optJSONObject("listing");
                if (data == null) data = response; // Fallback if direct object
                product = AppDataStore.ProductRecord.fromJson(data);
                if (product != null) {
                    renderProduct();
                } else {
                    tryLocalFallback(id);
                }
            }

            @Override
            public void onError(String message) {
                tryLocalFallback(id);
            }
        });
    }

    private void tryLocalFallback(String id) {
        product = AppDataStore.getListing(this, id);
        if (product != null) renderProduct();
        else {
            Toast.makeText(this, "Product not found", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void renderProduct() {
        carousel = findViewById(R.id.productCarousel);
        layoutIndicators = findViewById(R.id.layoutIndicators);
        
        // Shared Element Transition target
        ViewCompat.setTransitionName(carousel, "product_image_hero");

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
        ((TextView) findViewById(R.id.productPrice)).setText("RM " + product.price);
        ((TextView) findViewById(R.id.productMeta)).setText("★ " + product.rating + "  •  " + product.distance + "  •  " + product.category);
        ((TextView) findViewById(R.id.productDescription)).setText(product.description);
        ((TextView) findViewById(R.id.sellerName)).setText(product.seller);
        findViewById(R.id.sellerName).setOnClickListener(v -> {
            Intent i = new Intent(this, SellerProfileActivity.class);
            i.putExtra(SellerProfileActivity.EXTRA_SELLER_NAME, product.seller);
            i.putExtra(SellerProfileActivity.EXTRA_SELLER_ID, product.ownerId);
            startActivity(i);
        });
        findViewById(R.id.btnReportListing).setOnClickListener(v -> {
            Intent i = new Intent(this, ReportActivity.class);
            i.putExtra(ReportActivity.EXTRA_TARGET_TYPE, "listing");
            i.putExtra(ReportActivity.EXTRA_TARGET_ID, product.id);
            i.putExtra(ReportActivity.EXTRA_TARGET_NAME, product.title);
            startActivity(i);
        });

        saveButton.setSelected(AppDataStore.isFavorite(this, product.id));
        saveButton.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            toggleFavorite();
        });

        MaterialButton message = findViewById(R.id.btnMessageSeller);
        if (product.owner) {
            message.setText("This is your listing");
            message.setEnabled(false);
            findViewById(R.id.btnMakeOffer).setEnabled(false);
            MaterialButton sold = findViewById(R.id.btnMarkSold);
            sold.setVisibility(android.view.View.VISIBLE);
            sold.setEnabled(product.available);
            sold.setOnClickListener(v -> {
                AppDataStore.markSold(this, product.id);
                Toast.makeText(this, "Listing marked as sold", Toast.LENGTH_SHORT).show();
                finish();
            });
        }

        message.setOnClickListener(v -> {
            if (!AppDataStore.isLoggedIn(this)) {
                Toast.makeText(this, "Please log in to contact the seller", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, LoginActivity.class));
                return;
            }
            Intent i = new Intent(this, ChatActivity.class);
            i.putExtra(ChatActivity.EXTRA_LISTING_ID, product.id);
            i.putExtra(ChatActivity.EXTRA_SELLER_ID, product.ownerId);
            i.putExtra(ChatActivity.EXTRA_OTHER_NAME, product.seller);
            startActivity(i);
        });

        findViewById(R.id.btnMakeOffer).setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            if (!AppDataStore.isLoggedIn(this)) {
                Toast.makeText(this, "Please log in to make an offer", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, LoginActivity.class));
                return;
            }
            showOfferDialog();
        });
    }

    private void setupIndicators(int count) {
        layoutIndicators.removeAllViews();
        if (count <= 1) return;
        for (int i = 0; i < count; i++) {
            android.widget.ImageView dot = new android.widget.ImageView(this);
            dot.setImageResource(R.drawable.dot_inactive);
            android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(24, 24);
            params.setMargins(8, 0, 8, 0);
            layoutIndicators.addView(dot, params);
        }
        updateIndicators(0);
    }

    private void updateIndicators(int position) {
        for (int i = 0; i < layoutIndicators.getChildCount(); i++) {
            android.widget.ImageView dot = (android.widget.ImageView) layoutIndicators.getChildAt(i);
            dot.setImageResource(i == position ? R.drawable.dot_active : R.drawable.dot_inactive);
        }
    }

    private void toggleFavorite() {
        if (!AppDataStore.isLoggedIn(this)) {
            Toast.makeText(this, "Login required to save items", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        String userId = AppDataStore.userId(this);
        NetworkApi.toggleFavorite(userId, product.id, new NetworkApi.Callback() {
            @Override
            public void onSuccess(org.json.JSONObject response) {
                AppDataStore.toggleFavorite(ProductDetailActivity.this, product.id);
                boolean isFav = AppDataStore.isFavorite(ProductDetailActivity.this, product.id);
                saveButton.setSelected(isFav);
                
                if (!isFav) {
                    Snackbar.make(saveButton, "Removed from saved items", Snackbar.LENGTH_LONG)
                            .setAction("UNDO", v -> toggleFavorite())
                            .setActionTextColor(getResources().getColor(R.color.pks_blue_variant))
                            .show();
                } else {
                    Toast.makeText(ProductDetailActivity.this, "Saved to your items", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String message) {
                // Local toggle if network fails
                AppDataStore.toggleFavorite(ProductDetailActivity.this, product.id);
                boolean isFav = AppDataStore.isFavorite(ProductDetailActivity.this, product.id);
                saveButton.setSelected(isFav);

                if (!isFav) {
                    Snackbar.make(saveButton, "Removed (offline)", Snackbar.LENGTH_LONG)
                            .setAction("UNDO", v -> toggleFavorite())
                            .show();
                } else {
                    Toast.makeText(ProductDetailActivity.this, "Saved (offline)", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void showOfferDialog() {
        EditText input = new EditText(this);
        input.setHint("Amount in RM");
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setSingleLine(true);

        new AlertDialog.Builder(this)
                .setTitle("Make an offer")
                .setMessage("Send an offer to " + product.seller)
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send offer", (d, w) -> {
                    String amount = input.getText().toString().trim();
                    if (amount.isEmpty()) {
                        Toast.makeText(this, "Enter an offer amount", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Professional Real Transaction Flow
                    String userId = AppDataStore.userId(this);
                    NetworkApi.addTransaction(userId, product.id, product.ownerId, amount, new NetworkApi.Callback() {
                        @Override
                        public void onSuccess(org.json.JSONObject response) {
                            // HYBRID RESILIENCE: Save locally for offline view, but it's officially on the server now
                            AppDataStore.addTransaction(ProductDetailActivity.this, product.id, product.title, "RM " + amount);
                            Toast.makeText(ProductDetailActivity.this, "Offer sent successfully!", Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onError(String message) {
                            // Fallback to local only for demo stability if network fails
                            AppDataStore.addTransaction(ProductDetailActivity.this, product.id, product.title, "RM " + amount);
                            Toast.makeText(ProductDetailActivity.this, "Offer sent (Local Only)", Toast.LENGTH_SHORT).show();
                        }
                    });
                }).show();
    }
}
