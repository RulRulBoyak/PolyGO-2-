package com.poliku.polygoplus;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.poliku.polygoplus.api.model.BaseResponse;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.ui.HapticManager;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class ReviewActivity extends AppCompatActivity {
    @Inject PolyGoRepository polyGoRepository;
    public static final String EXTRA_SELLER = "seller";
    public static final String EXTRA_TRANSACTION_ID = "transaction_id";
    public static final String EXTRA_LISTING_ID = "listing_id";

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
            String sellerName = seller;
            String txId = transactionId;
            String listingIdExtra = getIntent().getStringExtra(EXTRA_LISTING_ID);
            v.setEnabled(false);

            polyGoRepository.submitReview(AppDataStore.userId(this), sellerName, listingIdExtra, stars, comment, new Callback<BaseResponse>() {
                @Override public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        AppDataStore.addReview(ReviewActivity.this, sellerName, stars, comment);
                        if (txId != null) AppDataStore.markTransactionReviewed(ReviewActivity.this, txId);
                        Toast.makeText(ReviewActivity.this, "Thanks for helping the PKS community", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        String msg = (response.body() != null && response.body().getMessage() != null)
                                ? response.body().getMessage()
                                : getString(R.string.review_verified_buyer_message);
                        HapticManager.error(ReviewActivity.this);
                        Toast.makeText(ReviewActivity.this, msg, Toast.LENGTH_LONG).show();
                        v.setEnabled(true);
                    }
                }

                @Override public void onFailure(Call<BaseResponse> call, Throwable t) {
                    AppDataStore.addReview(ReviewActivity.this, sellerName, stars, comment);
                    if (txId != null) AppDataStore.markTransactionReviewed(ReviewActivity.this, txId);
                    Toast.makeText(ReviewActivity.this, R.string.review_saved_offline, Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
        });
    }
}
