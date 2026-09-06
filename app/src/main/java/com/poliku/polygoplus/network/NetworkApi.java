package com.poliku.polygoplus.network;

import android.os.Handler;
import android.os.Looper;

import com.poliku.polygoplus.data.AppDataStore;

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
    public static final String BASE_URL = "https://api.poliku.com/";

    private static android.content.Context appContext;

    private NetworkApi() { }

    public static void init(android.content.Context context) {
        appContext = context.getApplicationContext();
    }

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
            body.put("action", "profile");
            post("seller.php", body, callback);
        } catch (Exception e) {
            callback.onError("Request error");
        }
    }

    public static void getSellerMetrics(String userId, Callback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("seller_id", userId);
            body.put("action", "metrics");
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
        getListings(0, 50, "newest", callback); // Default large page for non-paginated callers
    }

    public static void getListings(int offset, int limit, Callback callback) {
        getListings(offset, limit, "newest", callback);
    }

    public static void getListings(int offset, int limit, String sort, Callback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("offset", offset);
            body.put("limit", limit);
            body.put("sort", sort);
            post("listings.php", body, callback);
        } catch (Exception e) {
            callback.onError("Request error");
        }
    }

    public static void searchListings(String query, String sort, Callback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("query", query);
            body.put("sort", sort);
            post("listings.php", body, callback);
        } catch (Exception e) {
            callback.onError("Request error");
        }
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

    public static void updateFcmToken(String userId, String token, Callback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("user_id", userId);
            body.put("fcm_token", token);
            post("update_profile.php", body, callback);
        } catch (Exception e) {
            callback.onError("Request error");
        }
    }

    public static void addListing(String ownerId, String title, String category, String price, String description, String image, String location, Callback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("owner_id", ownerId);
            body.put("title", title);
            body.put("category", category);
            body.put("price", price);
            body.put("description", description);
            body.put("image_url", image);
            body.put("location", location);
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

    public static void addTransaction(String userId, String listingId, String sellerId, String amount, Callback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("user_id", userId);
            body.put("listing_id", listingId);
            body.put("seller_id", sellerId);
            body.put("amount", amount);
            body.put("action", "add");
            post("transactions.php", body, callback);
        } catch (Exception e) {
            callback.onError("Request error");
        }
    }

    public static void getTransactions(String userId, Callback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("user_id", userId);
            body.put("action", "list");
            post("transactions.php", body, callback);
        } catch (Exception e) {
            callback.onError("Request error");
        }
    }

    public static void updateTransactionStatus(String userId, String transactionId, String status, Callback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("user_id", userId);
            body.put("transaction_id", transactionId);
            body.put("status", status);
            body.put("action", "update");
            post("transactions.php", body, callback);
        } catch (Exception e) {
            callback.onError("Request error");
        }
    }

    public static void uploadImage(android.content.Context context, android.net.Uri uri, Callback callback) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                String boundary = "Boundary-" + System.currentTimeMillis();
                connection = (HttpURLConnection) new URL(BASE_URL + "upload_image.php").openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

                // AUTHENTICATION: Add JWT Token if available
                if (appContext != null) {
                    String token = AppDataStore.userToken(appContext);
                    if (token != null && !token.isEmpty()) {
                        connection.setRequestProperty("Authorization", "Bearer " + token);
                    }
                }

                try (OutputStream out = connection.getOutputStream();
                     java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.OutputStreamWriter(out, StandardCharsets.UTF_8), true);
                     InputStream imageStream = context.getContentResolver().openInputStream(uri)) {

                    writer.append("--").append(boundary).append("\r\n");
                    writer.append("Content-Disposition: form-data; name=\"image\"; filename=\"image.jpg\"\r\n");
                    writer.append("Content-Type: image/jpeg\r\n\r\n");
                    writer.flush();

                    byte[] buffer = new byte[4096];
                    int n;
                    while ((n = imageStream.read(buffer)) != -1) out.write(buffer, 0, n);
                    out.flush();

                    writer.append("\r\n--").append(boundary).append("--\r\n");
                    writer.flush();
                }

                int status = connection.getResponseCode();
                InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
                String responseText = read(stream);
                JSONObject response = new JSONObject(responseText);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (response.optBoolean("success")) callback.onSuccess(response);
                    else callback.onError(response.optString("message", "Upload failed"));
                });

            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onError("Upload error: " + e.getMessage()));
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
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
                
                // AUTHENTICATION: Add JWT Token if available
                if (appContext != null) {
                    String token = AppDataStore.userToken(appContext);
                    if (token != null && !token.isEmpty()) {
                        connection.setRequestProperty("Authorization", "Bearer " + token);
                    }
                }

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
                        if (response.optBoolean("success")) {
                            callback.onSuccess(response);
                        } else {
                            String msg = response.optString("message", "The server rejected the request");
                            
                            // Rule 3.3: Human-readable Error Copywriting
                            String humanMsg = msg;
                            String lowerMsg = msg.toLowerCase();
                            if (lowerMsg.contains("unauthorized") || lowerMsg.contains("invalid token")) {
                                humanMsg = "Your session expired. Please sign in again.";
                                if (appContext != null) AppDataStore.logout(appContext);
                            } else if (lowerMsg.contains("failed to add") || lowerMsg.contains("listing error")) {
                                humanMsg = "We couldn't save your listing. Please check your data.";
                            } else if (lowerMsg.contains("duplicate") || lowerMsg.contains("already exists")) {
                                humanMsg = "This item or account already exists. Try something else!";
                            } else if (lowerMsg.contains("incorrect password") || lowerMsg.contains("invalid login")) {
                                humanMsg = "Check your ID or password and try again.";
                            } else if (lowerMsg.contains("database error") || lowerMsg.contains("sql")) {
                                humanMsg = "Our database is having a moment. We're fixing it!";
                            }
                            callback.onError(humanMsg);
                        }
                    });
                } catch (org.json.JSONException e) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onError("The connection was successful, but the data is temporarily unavailable."));
                }
            } catch (java.net.SocketTimeoutException e) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onError("Connection slow. Please try again when you have a better signal."));
            } catch (java.io.IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onError("Network offline. Please check your Wi-Fi or mobile data."));
            } catch (Exception e) {
                android.util.Log.e("NetworkApi", "Connection error for " + endpoint + ": " + e.getMessage(), e);
                new Handler(Looper.getMainLooper()).post(() -> callback.onError("Something went wrong on our end. We're working on it!"));
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
