package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.network.NetworkApi;

public class ProductDetailActivity extends AppCompatActivity {
    public static final String EXTRA_LISTING_ID = "listing_id";
    private AppDataStore.ProductRecord product;
    private com.google.android.material.button.MaterialButton saveButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_detail);

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
        ShapeableImageView image = findViewById(R.id.productImage);
        if (product.imageUri.isEmpty()) image.setImageResource(product.imageRes);
        else image.setImageURI(android.net.Uri.parse(product.imageUri));

        ((TextView) findViewById(R.id.productTitle)).setText(product.title);
        ((TextView) findViewById(R.id.productPrice)).setText("RM " + product.price);
        ((TextView) findViewById(R.id.productMeta)).setText("★ " + product.rating + "  •  " + product.distance + "  •  " + product.category);
        ((TextView) findViewById(R.id.productDescription)).setText(product.description);
        ((TextView) findViewById(R.id.sellerName)).setText(product.seller);

        saveButton.setSelected(AppDataStore.isFavorite(this, product.id));
        saveButton.setOnClickListener(v -> toggleFavorite());

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
            Intent i = new Intent(this, ChatActivity.class);
            i.putExtra(ChatActivity.EXTRA_LISTING_ID, product.id);
            i.putExtra(ChatActivity.EXTRA_SELLER_ID, product.ownerId);
            i.putExtra(ChatActivity.EXTRA_OTHER_NAME, product.seller);
            startActivity(i);
        });
        findViewById(R.id.btnMakeOffer).setOnClickListener(v -> showOfferDialog());
    }

    private void toggleFavorite() {
        String userId = AppDataStore.userId(this);
        NetworkApi.toggleFavorite(userId, product.id, new NetworkApi.Callback() {
            @Override
            public void onSuccess(org.json.JSONObject response) {
                AppDataStore.toggleFavorite(ProductDetailActivity.this, product.id);
                saveButton.setSelected(AppDataStore.isFavorite(ProductDetailActivity.this, product.id));
                Toast.makeText(ProductDetailActivity.this, saveButton.isSelected() ? "Saved to your items" : "Removed from saved items", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                // Local toggle if network fails
                AppDataStore.toggleFavorite(ProductDetailActivity.this, product.id);
                saveButton.setSelected(AppDataStore.isFavorite(ProductDetailActivity.this, product.id));
                Toast.makeText(ProductDetailActivity.this, saveButton.isSelected() ? "Saved (local only)" : "Removed (local only)", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showOfferDialog() {
        EditText input = new EditText(this); input.setHint("Amount in RM"); input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL); input.setSingleLine(true);
        new AlertDialog.Builder(this).setTitle("Make an offer").setMessage("Send an offer to " + product.seller).setView(input).setNegativeButton("Cancel", null).setPositiveButton("Send offer", (d,w) -> { String amount=input.getText().toString().trim(); if (amount.isEmpty()) { Toast.makeText(this,"Enter an offer amount",Toast.LENGTH_SHORT).show(); return; } AppDataStore.addTransaction(this, product.id, product.title, "RM " + amount); Toast.makeText(this,"Offer sent",Toast.LENGTH_SHORT).show(); }).show();
    }
}
