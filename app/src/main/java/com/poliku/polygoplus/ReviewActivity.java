package com.poliku.polygoplus;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.network.NetworkApi;

public class ReviewActivity extends AppCompatActivity {
    public static final String EXTRA_SELLER = "seller";
    public static final String EXTRA_TRANSACTION_ID = "transaction_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        String seller = getIntent().getStringExtra(EXTRA_SELLER);
        String transactionId = getIntent().getStringExtra(EXTRA_TRANSACTION_ID);
        ((TextView) findViewById(R.id.tvReviewSeller)).setText("Leave a 5-star review for " + (seller == null ? "this seller" : seller) + " after a successful campus meetup.");

        findViewById(R.id.btnSubmitReview).setOnClickListener(v -> {
            int stars = (int) ((RatingBar) findViewById(R.id.ratingBar)).getRating();
            String comment = ((EditText) findViewById(R.id.etReview)).getText().toString().trim();
            if (stars < 1) {
                Toast.makeText(this, "Choose a star rating", Toast.LENGTH_SHORT).show();
                return;
            }
            AppDataStore.addReview(this, seller, stars, comment);
            if (transactionId != null) AppDataStore.markTransactionReviewed(this, transactionId);
            NetworkApi.submitReview(AppDataStore.userId(this), seller, stars, comment, new NetworkApi.Callback() {
                @Override public void onSuccess(org.json.JSONObject response) { }
                @Override public void onError(String message) { }
            });
            Toast.makeText(this, "Thanks for helping the PKS community", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
