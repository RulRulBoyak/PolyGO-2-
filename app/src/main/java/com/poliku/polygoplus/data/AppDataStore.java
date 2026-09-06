package com.poliku.polygoplus.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.poliku.polygoplus.R;
import com.poliku.polygoplus.ui.PriceFormatter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Principal Rule 2.1 & 2.2: Defensive Immutability & Main-Thread Safety
 * Optimized local repository with strictly final records and background processing.
 */
public final class AppDataStore {
    private static final String PREFS = "polygo_local_store";
    
    // Rule 2.2: Dedicated background thread for disk I/O and parsing
    private static final ExecutorService diskExecutor = Executors.newSingleThreadExecutor();
    private static final String KEY_USER = "user";
    private static final String KEY_LISTINGS = "listings";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_THREADS = "threads";
    private static final String KEY_NOTIFICATIONS = "notifications";
    private static final String KEY_TRANSACTIONS = "transactions";
    private static final String KEY_VERIFICATION = "verification";
    private static final String KEY_VERIFICATION_STATUS = "verification_status";
    private static final String KEY_SEEDED = "seeded";
    private static final String KEY_REVIEWS = "reviews";
    private static final String KEY_REPORTS = "reports";
    private static final String KEY_SEARCH_HISTORY = "search_history";
    private static final String KEY_DRAFTS = "drafts";
    private static final String KEY_ONBOARDING = "onboarding_seen";
    private static final String KEY_MAINTENANCE = "maintenance_mode";
    private static final String KEY_BIO_LOCK = "bio_lock_enabled";

    public static final String[] PKS_LANDMARKS = {
            "Block A", "Block B", "Block C", "Cafeteria", "Library",
            "Main Hall", "Mosque", "Sports Complex", "Student Centre", "Near campus"
    };

    public static final String[] TRENDING_SEARCHES = {
            "Gaming Laptop", "Coffee Maker", "Textbooks", "Earbuds", "Repair"
    };

    private AppDataStore() {}

    private static SharedPreferences prefs(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            return EncryptedSharedPreferences.create(
                    context,
                    PREFS,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            // Fallback to standard prefs if encryption fails (e.g. key store issues)
            return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }
    }

    public static void initialize(Context context) {
        SharedPreferences p = prefs(context);
        if (!p.contains(KEY_USER)) {
            try {
                JSONObject user = new JSONObject();
                user.put("id", 1);
                user.put("name", "Amirul");
                user.put("studentId", "05DIT24F1029");
                user.put("email", "amirul@pks.edu.my");
                user.put("password", "password123");
                user.put("role", "Student");
                p.edit().putString(KEY_USER, user.toString()).apply();
            } catch (JSONException ignored) {}
        }
        if (p.getBoolean(KEY_SEEDED, false)) return;
        
        p.edit().putString(KEY_LISTINGS, "[]")
                .putStringSet(KEY_FAVORITES, new HashSet<>())
                .putString(KEY_THREADS, "[]")
                .putString(KEY_NOTIFICATIONS, "[]")
                .putString(KEY_TRANSACTIONS, "[]")
                .putString(KEY_REVIEWS, "[]")
                .putString(KEY_REPORTS, "[]")
                .putString(KEY_DRAFTS, "[]")
                .putBoolean(KEY_VERIFICATION, false)
                .putString(KEY_VERIFICATION_STATUS, "unverified")
                .putBoolean(KEY_SEEDED, true)
                .apply();
    }

    private static JSONArray array(Context context, String key) {
        try {
            return new JSONArray(prefs(context).getString(key, "[]"));
        } catch (JSONException e) { return new JSONArray(); }
    }

    private static void saveArray(Context context, String key, JSONArray value) {
        diskExecutor.execute(() -> 
            prefs(context).edit().putString(key, value.toString()).apply()
        );
    }

    public static boolean isLoggedIn(Context context) {
        return prefs(context).getBoolean("loggedIn", false);
    }

    public static void logout(Context context) {
        diskExecutor.execute(() -> 
            prefs(context).edit()
                .putBoolean("loggedIn", false)
                .remove("user_token")
                .apply()
        );
    }

    public static void saveRemoteSession(Context context, JSONObject user, String token) {
        if (user == null) return;
        diskExecutor.execute(() -> 
            prefs(context).edit()
                .putString(KEY_USER, user.toString())
                .putString("user_token", token)
                .putBoolean("loggedIn", true)
                .apply()
        );
    }

    public static String userToken(Context context) {
        return prefs(context).getString("user_token", "");
    }

    public static String userId(Context context) {
        try {
            String userJson = prefs(context).getString(KEY_USER, null);
            if (userJson == null) return "0";
            return new JSONObject(userJson).optString("id", "0");
        } catch (JSONException e) { return "0"; }
    }

    public static String userRole(Context context) {
        try {
            return new JSONObject(prefs(context).getString(KEY_USER, "{}")).optString("role", "Student");
        } catch (JSONException e) { return "Student"; }
    }

    public static String userName(Context context) {
        try {
            return new JSONObject(prefs(context).getString(KEY_USER, "{}")).optString("name", "PolyGo member");
        } catch (JSONException e) { return "PolyGo member"; }
    }

    public static String userEmail(Context context) {
        try {
            return new JSONObject(prefs(context).getString(KEY_USER, "{}")).optString("email", "");
        } catch (JSONException e) { return ""; }
    }

    public static String userMobile(Context context) {
        try {
            return new JSONObject(prefs(context).getString(KEY_USER, "{}")).optString("mobile", "");
        } catch (JSONException e) { return ""; }
    }

    public static String userProfilePic(Context context) {
        try {
            return new JSONObject(prefs(context).getString(KEY_USER, "{}")).optString("profile_pic_url", "");
        } catch (JSONException e) { return ""; }
    }

    public static boolean updateProfile(Context context, String name, String email, String mobile, String photo) {
        try {
            JSONObject user = new JSONObject(prefs(context).getString(KEY_USER, "{}"));
            user.put("name", name);
            user.put("email", email);
            user.put("mobile", mobile);
            user.put("profile_pic_url", photo);
            
            diskExecutor.execute(() -> 
                prefs(context).edit().putString(KEY_USER, user.toString()).apply()
            );
            return true;
        } catch (JSONException e) { return false; }
    }

    public static boolean changePassword(Context context, String password) {
        try {
            JSONObject user = new JSONObject(prefs(context).getString(KEY_USER, "{}"));
            user.put("password", password);
            diskExecutor.execute(() -> 
                prefs(context).edit().putString(KEY_USER, user.toString()).apply()
            );
            return true;
        } catch (JSONException e) { return false; }
    }

    public static void deleteAccount(Context context) {
        diskExecutor.execute(() -> 
            prefs(context).edit().remove(KEY_USER).putBoolean("loggedIn", false).apply()
        );
    }

    // --- Listings ---

    public static void updateListingsCache(Context context, JSONArray list) {
        saveArray(context, KEY_LISTINGS, list);
    }

    @NonNull
    public static List<ProductRecord> getListings(Context context) {
        List<ProductRecord> result = new ArrayList<>();
        JSONArray list = array(context, KEY_LISTINGS);
        for (int i = 0; i < list.length(); i++) {
            try {
                ProductRecord p = ProductRecord.fromJson(list.getJSONObject(i));
                if (p != null) result.add(p);
            } catch (JSONException ignored) {}
        }
        return Collections.unmodifiableList(result);
    }

    @Nullable
    public static ProductRecord getListing(Context context, String id) {
        for (ProductRecord item : getListings(context)) if (Objects.equals(item.id, id)) return item;
        return null;
    }

    public static ProductRecord addUserListing(Context context, String title, String category, String price, String description, String imageUri, String location) {
        JSONArray list = array(context, KEY_LISTINGS);
        String meetup = location == null || location.trim().isEmpty() ? "Near campus" : location.trim();
        ProductRecord product = new ProductRecord(UUID.randomUUID().toString(), title, userName(context), price, "New", meetup, R.drawable.bg_product_home, imageUri, category, description, true, true, userId(context));
        try {
            list.put(product.toJson());
            saveArray(context, KEY_LISTINGS, list);
        } catch (JSONException ignored) {}
        addNotification(context, "Your listing is live", title + " was added to the marketplace.");
        return product;
    }

    public static boolean markSold(Context context, String id) {
        JSONArray list = array(context, KEY_LISTINGS);
        for (int i = 0; i < list.length(); i++) {
            try {
                JSONObject o = list.getJSONObject(i);
                if (id.equals(o.optString("id"))) {
                    o.put("available", false);
                    saveArray(context, KEY_LISTINGS, list);
                    return true;
                }
            } catch (JSONException ignored) {}
        }
        return false;
    }

    public static boolean isFavorite(Context context, String id) {
        return favoriteIds(context).contains(id);
    }

    public static void toggleFavorite(Context context, String id) {
        diskExecutor.execute(() -> {
            Set<String> ids = favoriteIds(context);
            if (!ids.add(id)) ids.remove(id);
            prefs(context).edit().putStringSet(KEY_FAVORITES, ids).apply();
        });
    }

    private static Set<String> favoriteIds(Context context) {
        try {
            return new HashSet<>(prefs(context).getStringSet(KEY_FAVORITES, new HashSet<>()));
        } catch (ClassCastException e) {
            prefs(context).edit().remove(KEY_FAVORITES).apply();
            return new HashSet<>();
        }
    }

    public static List<ProductRecord> getFavorites(Context context) {
        Set<String> ids = favoriteIds(context);
        List<ProductRecord> result = new ArrayList<>();
        for (ProductRecord item : getListings(context)) if (ids.contains(item.id)) result.add(item);
        return result;
    }

    public static List<ProductRecord> getMyListings(Context context) {
        List<ProductRecord> result = new ArrayList<>();
        for (ProductRecord item : getListings(context)) if (item.owner) result.add(item);
        return result;
    }

    // --- Messaging ---

    public static String ensureThread(Context context, String listingId, String otherName) {
        for (ThreadRecord t : getThreads(context)) {
            if (Objects.equals(listingId, t.listingId) && Objects.equals(otherName, t.name)) return t.id;
        }
        JSONArray threads = array(context, KEY_THREADS);
        try {
            JSONObject t = new JSONObject();
            String id = UUID.randomUUID().toString();
            t.put("id", id); t.put("listingId", listingId); t.put("name", otherName); t.put("unread", false);
            t.put("messages", new JSONArray()); t.put("lastMessageTime", System.currentTimeMillis());
            threads.put(t); saveArray(context, KEY_THREADS, threads);
            return id;
        } catch (JSONException e) { return null; }
    }

    @NonNull
    public static List<ThreadRecord> getThreads(Context context) {
        List<ThreadRecord> result = new ArrayList<>();
        JSONArray list = array(context, KEY_THREADS);
        for (int i = 0; i < list.length(); i++)
            try { result.add(ThreadRecord.fromJson(list.getJSONObject(i))); } catch (JSONException ignored) {}
        return result;
    }

    public static ThreadRecord getThread(Context context, String id) {
        for (ThreadRecord t : getThreads(context)) if (Objects.equals(t.id, id)) return t;
        return null;
    }

    public static void markThreadRead(Context context, String id) {
        diskExecutor.execute(() -> {
            JSONArray threads = array(context, KEY_THREADS);
            for (int i = 0; i < threads.length(); i++) try {
                JSONObject t = threads.getJSONObject(i);
                if (Objects.equals(id, t.optString("id"))) { t.put("unread", false); saveArray(context, KEY_THREADS, threads); return; }
            } catch (JSONException ignored) {}
        });
    }

    public static void sendMessage(Context context, String threadId, String text) {
        diskExecutor.execute(() -> {
            JSONArray threads = array(context, KEY_THREADS);
            for (int i = 0; i < threads.length(); i++) try {
                JSONObject t = threads.getJSONObject(i);
                if (Objects.equals(threadId, t.optString("id"))) {
                    JSONArray msgs = t.optJSONArray("messages"); if (msgs == null) msgs = new JSONArray();
                    JSONObject m = new JSONObject();
                    m.put("sender", userName(context)); m.put("mine", true); m.put("text", text); m.put("time", System.currentTimeMillis());
                    msgs.put(m); t.put("messages", msgs); t.put("lastMessageTime", m.getLong("time"));
                    saveArray(context, KEY_THREADS, threads); return;
                }
            } catch (JSONException ignored) {}
        });
    }

    public static void addReplyToThread(Context context, String id, String name, String text) {
        diskExecutor.execute(() -> {
            JSONArray threads = array(context, KEY_THREADS);
            for (int i = 0; i < threads.length(); i++) try {
                JSONObject t = threads.getJSONObject(i);
                if (Objects.equals(id, t.optString("id"))) {
                    JSONArray msgs = t.optJSONArray("messages"); if (msgs == null) msgs = new JSONArray();
                    JSONObject m = new JSONObject();
                    m.put("sender", name); m.put("mine", false); m.put("text", text); m.put("time", System.currentTimeMillis());
                    msgs.put(m); t.put("messages", msgs); t.put("lastMessageTime", m.getLong("time")); t.put("unread", true);
                    saveArray(context, KEY_THREADS, threads); return;
                }
            } catch (JSONException ignored) {}
        });
    }

    // --- Transactions & Notifications ---

    public static void addTransaction(Context context, String listingId, String title, String amount) {
        diskExecutor.execute(() -> {
            JSONArray list = array(context, KEY_TRANSACTIONS);
            try {
                JSONObject o = new JSONObject();
                o.put("id", UUID.randomUUID().toString());
                o.put("listingId", listingId); o.put("title", title); o.put("amount", amount);
                o.put("status", "Offer sent"); o.put("time", System.currentTimeMillis()); o.put("reviewed", false);
                list.put(o); saveArray(context, KEY_TRANSACTIONS, list);
            } catch (JSONException ignored) {}
        });
    }

    @NonNull
    public static List<TransactionRecord> getTransactions(Context context) {
        List<TransactionRecord> result = new ArrayList<>();
        JSONArray list = array(context, KEY_TRANSACTIONS);
        for (int i = list.length() - 1; i >= 0; i--)
            try { result.add(TransactionRecord.fromJson(list.getJSONObject(i))); } catch (JSONException ignored) {}
        return result;
    }

    public static TransactionRecord getTransaction(Context context, String id) {
        for (TransactionRecord t : getTransactions(context)) if (Objects.equals(t.id, id)) return t;
        return null;
    }

    public static void updateTransactionStatus(Context context, String id, String status) {
        diskExecutor.execute(() -> {
            JSONArray list = array(context, KEY_TRANSACTIONS);
            for (int i = 0; i < list.length(); i++) try {
                JSONObject o = list.getJSONObject(i);
                if (Objects.equals(id, o.optString("id"))) { o.put("status", status); saveArray(context, KEY_TRANSACTIONS, list); return; }
            } catch (JSONException ignored) {}
        });
    }

    public static void markTransactionReviewed(Context context, String id) {
        diskExecutor.execute(() -> {
            JSONArray list = array(context, KEY_TRANSACTIONS);
            for (int i = 0; i < list.length(); i++) try {
                JSONObject o = list.getJSONObject(i);
                if (Objects.equals(id, o.optString("id"))) { o.put("reviewed", true); saveArray(context, KEY_TRANSACTIONS, list); return; }
            } catch (JSONException ignored) {}
        });
    }

    public static List<NotificationRecord> getNotifications(Context context) {
        List<NotificationRecord> result = new ArrayList<>();
        JSONArray list = array(context, KEY_NOTIFICATIONS);
        for (int i = list.length() - 1; i >= 0; i--)
            try { result.add(NotificationRecord.fromJson(list.getJSONObject(i))); } catch (JSONException ignored) {}
        return result;
    }

    public static void addNotification(Context context, String title, String body) {
        diskExecutor.execute(() -> {
            JSONArray list = array(context, KEY_NOTIFICATIONS);
            try {
                JSONObject o = new JSONObject();
                o.put("id", UUID.randomUUID().toString()); o.put("title", title); o.put("body", body);
                o.put("time", System.currentTimeMillis()); o.put("read", false);
                list.put(o); saveArray(context, KEY_NOTIFICATIONS, list);
            } catch (JSONException ignored) {}
        });
    }

    public static void markNotificationsRead(Context context) {
        diskExecutor.execute(() -> {
            JSONArray list = array(context, KEY_NOTIFICATIONS);
            for (int i = 0; i < list.length(); i++) try { list.getJSONObject(i).put("read", true); } catch (JSONException ignored) {}
            saveArray(context, KEY_NOTIFICATIONS, list);
        });
    }

    public static void markNotificationRead(Context context, String id) {
        diskExecutor.execute(() -> {
            JSONArray list = array(context, KEY_NOTIFICATIONS);
            for (int i = 0; i < list.length(); i++) try {
                JSONObject o = list.getJSONObject(i);
                if (Objects.equals(id, o.optString("id"))) { o.put("read", true); saveArray(context, KEY_NOTIFICATIONS, list); return; }
            } catch (JSONException ignored) {}
        });
    }

    public static void addReport(Context context, String targetType, String targetId, String targetName, String reason, String details) {
        diskExecutor.execute(() -> {
            JSONArray list = array(context, KEY_REPORTS);
            try {
                JSONObject o = new JSONObject();
                o.put("id", UUID.randomUUID().toString());
                o.put("targetType", targetType); o.put("targetId", targetId); o.put("targetName", targetName);
                o.put("reason", reason); o.put("details", details); o.put("time", System.currentTimeMillis());
                list.put(o); saveArray(context, KEY_REPORTS, list);
                addNotification(context, "Report received", "Our moderators will review " + targetName);
            } catch (JSONException ignored) {}
        });
    }

    public static void addReview(Context context, String seller, int stars, String comment) {
        diskExecutor.execute(() -> {
            JSONArray list = array(context, KEY_REVIEWS);
            try {
                JSONObject o = new JSONObject();
                o.put("id", UUID.randomUUID().toString());
                o.put("seller", seller); o.put("reviewer", userName(context)); o.put("stars", stars);
                o.put("comment", comment); o.put("time", System.currentTimeMillis());
                list.put(o); saveArray(context, KEY_REVIEWS, list);
            } catch (JSONException ignored) {}
        });
    }

    // --- Search & Settings ---

    public static void addSearchQuery(Context context, String q) {
        if (q == null || q.trim().isEmpty()) return;
        diskExecutor.execute(() -> {
            JSONArray list = array(context, KEY_SEARCH_HISTORY);
            JSONArray next = new JSONArray(); next.put(q.trim());
            for (int i = 0; i < list.length() && next.length() < 8; i++) {
                String s = list.optString(i); if (!s.equalsIgnoreCase(q)) next.put(s);
            }
            saveArray(context, KEY_SEARCH_HISTORY, next);
        });
    }

    public static List<String> getSearchHistory(Context context) {
        List<String> res = new ArrayList<>();
        JSONArray list = array(context, KEY_SEARCH_HISTORY);
        for (int i = 0; i < list.length(); i++) res.add(list.optString(i));
        return res;
    }

    public static void clearSearchHistory(Context context) { 
        diskExecutor.execute(() -> saveArray(context, KEY_SEARCH_HISTORY, new JSONArray())); 
    }

    public static void saveDraft(Context context, String t, String c, String p, String d, String i, String l) {
        diskExecutor.execute(() -> {
            JSONArray list = array(context, KEY_DRAFTS);
            try {
                JSONObject o = new JSONObject();
                o.put("id", UUID.randomUUID().toString()); o.put("title", t); o.put("category", c); o.put("price", p);
                o.put("description", d); o.put("imageUri", i); o.put("location", l); o.put("time", System.currentTimeMillis());
                list.put(o); saveArray(context, KEY_DRAFTS, list);
            } catch (JSONException ignored) {}
        });
    }

    public static List<JSONObject> getDrafts(Context context) {
        List<JSONObject> res = new ArrayList<>();
        JSONArray list = array(context, KEY_DRAFTS);
        for (int i = list.length() - 1; i >= 0; i--) res.add(list.optJSONObject(i));
        return res;
    }

    public static JSONObject getDraft(Context context, String id) {
        for (JSONObject o : getDrafts(context)) if (Objects.equals(id, o.optString("id"))) return o;
        return null;
    }

    public static void deleteDraft(Context context, String id) {
        diskExecutor.execute(() -> {
            JSONArray list = array(context, KEY_DRAFTS); JSONArray next = new JSONArray();
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.optJSONObject(i); if (o != null && !Objects.equals(id, o.optString("id"))) next.put(o);
            }
            saveArray(context, KEY_DRAFTS, next);
        });
    }

    public static void setOnboardingSeen(Context context) { 
        diskExecutor.execute(() -> prefs(context).edit().putBoolean(KEY_ONBOARDING, true).apply()); 
    }
    
    public static boolean hasSeenOnboarding(Context context) { return prefs(context).getBoolean(KEY_ONBOARDING, false); }
    
    public static void setMaintenanceMode(Context context, boolean on) { 
        diskExecutor.execute(() -> prefs(context).edit().putBoolean(KEY_MAINTENANCE, on).apply()); 
    }

    public static boolean isBioLockEnabled(Context context) {
        return prefs(context).getBoolean(KEY_BIO_LOCK, false);
    }

    public static void setBioLockEnabled(Context context, boolean enabled) {
        diskExecutor.execute(() -> prefs(context).edit().putBoolean(KEY_BIO_LOCK, enabled).apply());
    }

    public static String verificationStatus(Context context) {
        return prefs(context).getString(KEY_VERIFICATION_STATUS, "unverified");
    }

    public static void submitVerification(Context context) {
        diskExecutor.execute(() -> prefs(context).edit().putString(KEY_VERIFICATION_STATUS, "pending").apply());
    }

    public static void approvePendingVerification(Context context) {
        diskExecutor.execute(() -> prefs(context).edit().putBoolean(KEY_VERIFICATION, true).putString(KEY_VERIFICATION_STATUS, "approved").apply());
    }

    public static boolean verifyAccount(Context context, String id, String email) {
        try {
            JSONObject u = new JSONObject(prefs(context).getString(KEY_USER, "{}"));
            return id.equalsIgnoreCase(u.optString("studentId")) && email.equalsIgnoreCase(u.optString("email"));
        } catch (JSONException e) { return false; }
    }

    public static String requestPasswordReset(Context context, String idOrEmail) {
        try {
            JSONObject u = new JSONObject(prefs(context).getString(KEY_USER, "{}"));
            if (idOrEmail.equalsIgnoreCase(u.optString("studentId")) || idOrEmail.equalsIgnoreCase(u.optString("email"))) {
                String temp = "PKS" + (1000 + Math.abs(idOrEmail.hashCode() % 9000));
                u.put("password", temp); prefs(context).edit().putString(KEY_USER, u.toString()).apply();
                return temp;
            }
        } catch (JSONException ignored) {}
        return null;
    }

    public static List<ProductRecord> getListingsBySeller(Context context, String name, String id) {
        List<ProductRecord> res = new ArrayList<>();
        for (ProductRecord p : getListings(context)) {
            if (name.equalsIgnoreCase(p.seller) || (id != null && Objects.equals(id, p.ownerId))) res.add(p);
        }
        return res;
    }

    public static int countSoldBySeller(Context context, String name, String id) {
        int count = 0;
        for (ProductRecord p : getListingsBySeller(context, name, id)) if (!p.available) count++;
        return count;
    }

    public static float averageRatingForSeller(Context context, String name) {
        List<ReviewRecord> rs = getReviewsForSeller(context, name);
        if (rs.isEmpty()) return 0f;
        float sum = 0; for (ReviewRecord r : rs) sum += r.stars;
        return sum / rs.size();
    }

    public static List<ReviewRecord> getReviewsForSeller(Context context, String name) {
        List<ReviewRecord> res = new ArrayList<>();
        JSONArray list = array(context, KEY_REVIEWS);
        for (int i = 0; i < list.length(); i++) try {
            ReviewRecord r = ReviewRecord.fromJson(list.getJSONObject(i));
            if (name.equalsIgnoreCase(r.seller)) res.add(r);
        } catch (JSONException ignored) {}
        return res;
    }

    // --- IMMUTABLE RECORDS ---

    public static final class ProductRecord {
        @NonNull public final String id, title, seller, price, rating, distance, imageUri, category, description, ownerId;
        public final int imageRes; public final boolean owner, available;

        public ProductRecord(@NonNull String id, @NonNull String title, @NonNull String seller, @NonNull String price, @NonNull String rating, @NonNull String distance, int imageRes, @NonNull String imageUri, @NonNull String category, @NonNull String description, boolean owner, boolean available, @NonNull String ownerId) {
            this.id = id; this.title = title; this.seller = seller; this.price = price; this.rating = rating; this.distance = distance; this.imageRes = imageRes; this.imageUri = imageUri; this.category = category; this.description = description; this.owner = owner; this.available = available; this.ownerId = ownerId;
        }

        public ProductRecord withOwnerStatus(boolean isOwner) {
            return new ProductRecord(id, title, seller, price, rating, distance, imageRes, imageUri, category, description, isOwner, available, ownerId);
        }

        public List<String> imageList() {
            if (imageUri.isEmpty()) return Collections.emptyList();
            List<String> res = new ArrayList<>();
            String[] parts = imageUri.split("\\|");
            Collections.addAll(res, parts);
            return Collections.unmodifiableList(res);
        }

        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("title", title); o.put("seller", seller); o.put("price", price); o.put("rating", rating); o.put("distance", distance); o.put("imageRes", imageRes); o.put("imageUri", imageUri); o.put("category", category); o.put("description", description); o.put("owner", owner); o.put("available", available); o.put("owner_id", ownerId);
            return o;
        }

        public static ProductRecord fromJson(JSONObject o) {
            if (o == null) return null;
            String price = PriceFormatter.format(o.optString("price", "0"));
            return new ProductRecord(o.optString("id", "0"), o.optString("title", "Item"), o.optString("seller", "User"), price, o.optString("rating", "4.5"), o.optString("distance", "Near"), o.optInt("imageRes", R.drawable.bg_product_home), o.optString("imageUri", ""), o.optString("category", "General"), o.optString("description", ""), o.optBoolean("owner", false), o.optBoolean("available", true), o.optString("owner_id", "0"));
        }
    }

    public static final class TransactionRecord {
        @NonNull public final String id, listingId, title, amount, status, location, seller;
        public final long time; public final boolean reviewed;

        public TransactionRecord(@NonNull String id, @NonNull String listingId, @NonNull String title, @NonNull String amount, @NonNull String status, @NonNull String location, @NonNull String seller, long time, boolean reviewed) {
            this.id = id; this.listingId = listingId; this.title = title; this.amount = amount; this.status = status; this.location = location; this.seller = seller; this.time = time; this.reviewed = reviewed;
        }

        public static TransactionRecord fromJson(JSONObject o) {
            return new TransactionRecord(o.optString("id", ""), o.optString("listingId", ""), o.optString("title", "Deal"), o.optString("amount", "0"), o.optString("status", "Sent"), o.optString("location", "Campus"), o.optString("seller", "User"), o.optLong("time", 0), o.optBoolean("reviewed", false));
        }
    }

    public static final class ThreadRecord {
        @NonNull public final String id, listingId, name, preview; @NonNull public final JSONArray messages;
        public final long lastMessageTime; public final boolean unread;

        public ThreadRecord(@NonNull String id, @NonNull String listingId, @NonNull String name, @NonNull String preview, @NonNull JSONArray messages, long lastMessageTime, boolean unread) {
            this.id = id; this.listingId = listingId; this.name = name; this.preview = preview; this.messages = messages; this.lastMessageTime = lastMessageTime; this.unread = unread;
        }

        public static ThreadRecord fromJson(JSONObject o) {
            JSONArray msgs = o.optJSONArray("messages");
            if (msgs == null) msgs = new JSONArray();
            return new ThreadRecord(
                    o.optString("id", ""),
                    o.optString("listingId", ""),
                    o.optString("name", "Chat"),
                    o.optString("last_message", ""),
                    msgs,
                    o.optLong("lastMessageTime", 0),
                    o.optBoolean("unread", false));
        }
    }

    public static final class ReviewRecord {
        @NonNull public final String id, seller, reviewer, comment; public final int stars; public final long time;

        public ReviewRecord(@NonNull String id, @NonNull String seller, @NonNull String reviewer, @NonNull String comment, int stars, long time) {
            this.id = id; this.seller = seller; this.reviewer = reviewer; this.comment = comment; this.stars = stars; this.time = time;
        }

        public static ReviewRecord fromJson(JSONObject o) {
            return new ReviewRecord(o.optString("id", ""), o.optString("seller", ""), o.optString("reviewer", ""), o.optString("comment", ""), o.optInt("stars", 5), o.optLong("time", 0));
        }
    }

    public static final class NotificationRecord {
        @NonNull public final String id, title, body; public final long time; public final boolean read;

        public NotificationRecord(@NonNull String id, @NonNull String title, @NonNull String body, long time, boolean read) {
            this.id = id; this.title = title; this.body = body; this.time = time; this.read = read;
        }

        public static NotificationRecord fromJson(JSONObject o) {
            return new NotificationRecord(o.optString("id", ""), o.optString("title", "Alert"), o.optString("body", ""), o.optLong("time", 0), o.optBoolean("read", false));
        }
    }
}
