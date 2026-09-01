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

    public static void getStatus(Callback callback) {
        post("status.php", new JSONObject(), callback);
    }

    public static void getSeller(String sellerId, String sellerName, Callback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("seller_id", sellerId);
            body.put("seller_name", sellerName);
            post("seller.php", body, callback);
        } catch (Exception e) {
            callback.onError("Request error");
        }
    }

    public static void submitReport(String userId, String targetType, String targetId, String reason, String details, Callback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("user_id", userId);
            body.put("target_type", targetType);
            body.put("target_id", targetId);
            body.put("reason", reason);
            body.put("details", details);
            post("report.php", body, callback);
        } catch (Exception e) {
            callback.onError("Request error");
        }
    }

    public static void submitReview(String userId, String seller, int stars, String comment, Callback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("user_id", userId);
            body.put("seller", seller);
            body.put("stars", stars);
            body.put("comment", comment);
            post("review.php", body, callback);
        } catch (Exception e) {
            callback.onError("Request error");
        }
    }

    public static void forgotPassword(String studentIdOrEmail, Callback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("identifier", studentIdOrEmail);
            post("forgot_password.php", body, callback);
        } catch (Exception e) {
            callback.onError("Request error");
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

    public static void updateProfile(String userId, String name, String email, String mobile, String photo, Callback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("user_id", userId);
            body.put("full_name", name);
            body.put("email", email);
            body.put("mobile", mobile);
            body.put("profile_pic_url", photo);
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

                try {
                    JSONObject response = new JSONObject(responseText);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (response.optBoolean("success")) callback.onSuccess(response);
                        else
                            callback.onError(response.optString("message", "The server rejected the request"));
                    });
                } catch (org.json.JSONException e) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onError("The server returned an invalid response. Please try again later."));
                }
            } catch (java.net.SocketTimeoutException e) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onError("The request timed out. Please check your connection."));
            } catch (java.io.IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onError("Network error. Cannot reach the PolyGo server."));
            } catch (Exception e) {
                android.util.Log.e("NetworkApi", "Connection error for " + endpoint + ": " + e.getMessage(), e);
                new Handler(Looper.getMainLooper()).post(() -> callback.onError("An unexpected error occurred: " + e.getLocalizedMessage()));
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
