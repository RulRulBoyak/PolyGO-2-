package com.poliku.polygoplus.network;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.data.PolyGoRepository;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.Collections;
import java.util.List;

/**
 * Server-side AI proxy: the image is sent to ai_suggest.php which calls Gemini.
 * The API key now lives in backend/secrets.php, never in the APK.
 */
public final class AiHelper {

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public interface AiCallback {
        void onResult(String title, String price, String description, String category,
                      List<String> tags, String condition, double confidence);
        void onError(String error);
    }

    private AiHelper() {}

    private static void onMain(Runnable runnable) {
        MAIN_HANDLER.post(runnable);
    }

    public static void suggestListingDetails(PolyGoRepository repo, Context context, Uri imageUri, AiCallback callback) {
        suggest(repo, context, imageUri, "product", callback);
    }

    public static void suggestServiceDetails(PolyGoRepository repo, Context context, Uri imageUri, AiCallback callback) {
        suggest(repo, context, imageUri, "service", callback);
    }

    private static void suggest(PolyGoRepository repo, Context context, Uri imageUri,
                                String mode, AiCallback callback) {
        repo.aiSuggest(context.getApplicationContext(), imageUri, mode, new Callback<PolyGoApi.AiSuggestResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.AiSuggestResponse> call, Response<PolyGoApi.AiSuggestResponse> response) {
                PolyGoApi.AiSuggestResponse body = response.body();
                if (response.isSuccessful() && body != null && body.isSuccess()) {
                    String title = safe(body.title);
                    String price = safe(body.price);
                    String description = safe(body.description);
                    if (title.isEmpty() || price.isEmpty() || description.isEmpty()) {
                        onMain(() -> callback.onError("AI returned an incomplete suggestion. Try another photo."));
                        return;
                    }
                    List<String> tags = body.tags == null ? Collections.emptyList() : body.tags;
                    onMain(() -> callback.onResult(title, price, description,
                            safe(body.category), tags, safe(body.condition), body.confidence));
                } else {
                    String msg = "AI suggestion failed";
                    if (body != null && body.getMessage() != null) {
                        msg = body.getMessage();
                    }
                    final String errorMsg = msg;
                    onMain(() -> callback.onError(errorMsg));
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.AiSuggestResponse> call, Throwable t) {
                onMain(() -> callback.onError("AI is temporarily unavailable. Your draft is safe; try again."));
            }
        });
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
