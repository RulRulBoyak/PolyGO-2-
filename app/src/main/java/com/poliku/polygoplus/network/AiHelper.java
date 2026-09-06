package com.poliku.polygoplus.network;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONObject;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class AiHelper {
    // Replace with your real Gemini API Key from Google AI Studio
    private static final String API_KEY = "YOUR_GEMINI_API_KEY";

    public interface Callback {
        void onResult(String title, String price, String description);
        void onError(String error);
    }

    private AiHelper() {}

    public static void suggestListingDetails(Context context, Uri imageUri, Callback callback) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(), imageUri);
            
            // Limit bitmap size for AI processing
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 512, 512, true);

            GenerativeModel gm = new GenerativeModel("gemini-1.5-flash", API_KEY);
            GenerativeModelFutures model = GenerativeModelFutures.from(gm);

            Content content = new Content.Builder()
                    .addText("Analyze this image of an item being sold on a college campus. " +
                            "Suggest a professional product Title, a fair Price in RM (Ringgit Malaysia), " +
                            "and a short, attractive Description. Format your response strictly as JSON: " +
                            "{\"title\": \"...\", \"price\": \"...\", \"description\": \"...\"}")
                    .addImage(scaled)
                    .build();

            Executor executor = Executors.newSingleThreadExecutor();
            ListenableFuture<GenerateContentResponse> response = model.generateContent(content);

            Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
                @Override
                public void onSuccess(GenerateContentResponse result) {
                    try {
                        String text = result.getText();
                        // Strip markdown code blocks if present
                        if (text.contains("```json")) {
                            text = text.substring(text.indexOf("```json") + 7, text.lastIndexOf("```"));
                        } else if (text.contains("```")) {
                            text = text.substring(text.indexOf("```") + 3, text.lastIndexOf("```"));
                        }
                        
                        JSONObject json = new JSONObject(text.trim());
                        String title = json.optString("title", "Campus Item");
                        String price = json.optString("price", "10.00").replaceAll("[^0-9.]", "");
                        String desc = json.optString("description", "Good condition.");
                        
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> 
                            callback.onResult(title, price, desc)
                        );
                    } catch (Exception e) {
                        onFailure(e);
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> 
                        callback.onError("AI failed: " + t.getMessage())
                    );
                }
            }, executor);

        } catch (Exception e) {
            callback.onError("AI Setup Error: " + e.getMessage());
        }
    }
}
