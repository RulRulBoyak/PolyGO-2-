package com.poliku.polygoplus.network;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Small API client for the local PHP/MySQL server. Change BASE_URL for your network. */
public final class NetworkApi {
    // Android emulator -> laptop. For a physical phone, use your laptop Wi-Fi IP instead.
    public static final String BASE_URL = "http://10.0.2.2/polygo-api/";

    private NetworkApi() { }

    public interface Callback {
        void onSuccess(JSONObject response);
        void onError(String message);
    }

    public static void login(String studentId, String password, Callback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("student_id", studentId);
            body.put("password", password);
            post("login.php", body, callback);
        } catch (Exception e) {
            callback.onError("Could not prepare login request");
        }
    }

    public static void register(String name, String studentId, String email, String password, Callback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("full_name", name);
            body.put("student_id", studentId);
            body.put("email", email);
            body.put("password", password);
            post("register.php", body, callback);
        } catch (Exception e) {
            callback.onError("Could not prepare registration request");
        }
    }

    public static void getListings(Callback callback) {
        post("listings.php", new JSONObject(), callback);
    }

    public static void getListing(String id, Callback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("id", id);
            post("listings.php", body, callback);
        } catch (Exception e) {
            callback.onError("Request error");
        }
    }

    public static void updateProfile(String userId, String name, String email, String mobile, Callback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("user_id", userId);
            body.put("full_name", name);
            body.put("email", email);
            body.put("mobile", mobile);
            post("update_profile.php", body, callback);
        } catch (Exception e) {
            callback.onError("Request error");
        }
    }

    public static void addListing(String ownerId, String title, String category, String price, String description, String image, Callback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("owner_id", ownerId);
            body.put("title", title);
            body.put("category", category);
            body.put("price", price);
            body.put("description", description);
            body.put("image_url", image);
            post("add_listing.php", body, callback);
        } catch (Exception e) {
            callback.onError("Request error");
        }
    }

    public static void toggleFavorite(String userId, String listingId, Callback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("user_id", userId);
            body.put("listing_id", listingId);
            body.put("action", "toggle");
            post("favorites.php", body, callback);
        } catch (Exception e) {
            callback.onError("Request error");
        }
    }

    public static void getFavorites(String userId, Callback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("user_id", userId);
            post("favorites.php", body, callback);
        } catch (Exception e) {
            callback.onError("Request error");
        }
    }

    public static void getThreads(String userId, Callback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("user_id", userId);
            post("messages.php", body, callback);
        } catch (Exception e) {
            callback.onError("Request error");
        }
    }

    public static void getMessages(String userId, String threadId, Callback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("user_id", userId);
            body.put("thread_id", threadId);
            body.put("action", "messages");
            post("messages.php", body, callback);
        } catch (Exception e) {
            callback.onError("Request error");
        }
    }

    public static void sendMessage(String userId, String threadId, String listingId, String receiverId, String text, Callback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("user_id", userId);
            body.put("thread_id", threadId);
            body.put("listing_id", listingId);
            body.put("receiver_id", receiverId);
            body.put("text", text);
            body.put("action", "send");
            post("messages.php", body, callback);
        } catch (Exception e) {
            callback.onError("Request error");
        }
    }

    public static void getNotifications(String userId, Callback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("user_id", userId);
            post("notifications.php", body, callback);
        } catch (Exception e) {
            callback.onError("Request error");
        }
    }

    public static void markNotificationsRead(String userId, Callback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("user_id", userId);
            body.put("action", "read");
            post("notifications.php", body, callback);
        } catch (Exception e) {
            callback.onError("Request error");
        }
    }

    private static void post(String endpoint, JSONObject body, Callback callback) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(BASE_URL + endpoint).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setDoOutput(true);
                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(payload);
                }

                int status = connection.getResponseCode();
                InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
                String responseText = read(stream);
                
                android.util.Log.d("NetworkApi", "Response from " + endpoint + " (Status " + status + "): " + responseText);
                
                JSONObject response = new JSONObject(responseText);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (response.optBoolean("success")) callback.onSuccess(response);
                    else callback.onError(response.optString("message", "The server rejected the request"));
                });
            } catch (Exception e) {
                android.util.Log.e("NetworkApi", "Connection error for " + endpoint + ": " + e.getMessage(), e);
                new Handler(Looper.getMainLooper()).post(() -> callback.onError(
                        "Cannot reach the PolyGo server. Error: " + e.getMessage()));
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "{}";
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) output.append(line);
        }
        return output.toString();
    }
}
