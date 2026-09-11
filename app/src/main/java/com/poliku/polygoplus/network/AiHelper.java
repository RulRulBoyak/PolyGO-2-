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

/**
 * Server-side AI proxy: the image is sent to ai_suggest.php which calls Gemini.
 * The API key now lives in backend/secrets.php, never in the APK.
 */
public final class AiHelper {

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public interface AiCallback {
        void onResult(String title, String price, String description);
        void onError(String error);
    }

    private AiHelper() {}

    private static void onMain(Runnable runnable) {
        MAIN_HANDLER.post(runnable);
    }

    public static void suggestListingDetails(PolyGoRepository repo, Context context, Uri imageUri, AiCallback callback) {
        repo.aiSuggest(context, imageUri, new Callback<PolyGoApi.AiSuggestResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.AiSuggestResponse> call, Response<PolyGoApi.AiSuggestResponse> response) {
                PolyGoApi.AiSuggestResponse body = response.body();
                if (response.isSuccessful() && body != null && body.isSuccess()) {
                    onMain(() -> callback.onResult(
                            body.title != null ? body.title : "Campus Item",
                            body.price != null ? body.price : "",
                            body.description != null ? body.description : ""));
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
                onMain(() -> callback.onError(t.getLocalizedMessage() != null
                        ? "AI request failed: " + t.getLocalizedMessage() : "AI request failed"));
            }
        });
    }
}