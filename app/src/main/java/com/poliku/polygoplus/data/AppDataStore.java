package com.poliku.polygoplus.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.R;
import com.poliku.polygoplus.data.local.entity.ListingEntity;
import com.poliku.polygoplus.ui.PriceFormatter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final ExecutorService DISK_EXECUTOR = Executors.newSingleThreadExecutor();
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
    private static final String KEY_MEETUP_DISCLOSURE = "meetup_disclosure_seen";

    public static final String[] PKS_LANDMARKS = {
        "Block A", "Block B", "Block C", "Cafeteria", "Library",
        "Main Hall", "Mosque", "Sports Complex", "Student Centre", "Near campus", "Other"
    };

    public static final String[] TRENDING_SEARCHES = {
        "Gaming Laptop", "Coffee Maker", "Textbooks", "Earbuds", "Repair"
    };

    private AppDataStore() {}

    private static volatile MasterKey masterKey;
    private static volatile SharedPreferences cachedPrefs;

    private static SharedPreferences prefs(Context context) {
        SharedPreferences local = cachedPrefs;
        if (local == null) {
            synchronized (AppDataStore.class) {
                local = cachedPrefs;
                if (local == null) {
                    try {
                        if (masterKey == null) {
                            masterKey = new MasterKey.Builder(context.getApplicationContext())
                                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                                    .build();
                        }
                        cachedPrefs = EncryptedSharedPreferences.create(
                                context.getApplicationContext(),
                                PREFS,
                                masterKey,
                                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                        );
                        local = cachedPrefs;
                    } catch (Exception e) {
                        throw new RuntimeException("Security Failure: Could not initialize EncryptedSharedPreferences", e);
                    }
                }
            }
        }
        return local;
    }

    public static void initialize(Context context) {
        Context app = context.getApplicationContext();
        DISK_EXECUTOR.execute(() -> {
            SharedPreferences p = prefs(app);
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
                    .commit();
        });
    }

    private static JSONArray array(Context context, String key) {
        try {
            return new JSONArray(prefs(context).getString(key, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private static void saveArray(Context context, String key, JSONArray value) {
        DISK_EXECUTOR.execute(() -> 
            prefs(context).edit().putString(key, value.toString()).apply()
        );
    }

    public static boolean isLoggedIn(Context context) {
        return prefs(context).getBoolean("loggedIn", false);
    }

    public static void logout(Context context) {
        DISK_EXECUTOR.execute(() -> 
            prefs(context).edit()
                .putBoolean("loggedIn", false)
                .remove("user_token")
                .apply()
        );
    }

    public static void saveRemoteSession(Context context, JSONObject user, String token) {
        if (user == null) return;
        DISK_EXECUTOR.execute(() -> 
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
        } catch (JSONException e) {
            return "0";
        }
    }

    public static String userRole(Context context) {
        try {
            return new JSONObject(prefs(context).getString(KEY_USER, "{}")).optString("role", "Student");
        } catch (JSONException e) {
            return "Student";
        }
    }

    public static String userName(Context context) {
        try {
            return new JSONObject(prefs(context).getString(KEY_USER, "{}")).optString("name", "PolyGo member");
        } catch (JSONException e) {
            return "PolyGo member";
        }
    }

    public static String userEmail(Context context) {
        try {
            return new JSONObject(prefs(context).getString(KEY_USER, "{}")).optString("email", "");
        } catch (JSONException e) {
            return "";
        }
    }

    public static String userMobile(Context context) {
        try {
            return new JSONObject(prefs(context).getString(KEY_USER, "{}")).optString("mobile", "");
        } catch (JSONException e) {
            return "";
        }
    }

    public static String userProfilePic(Context context) {
        try {
            return new JSONObject(prefs(context).getString(KEY_USER, "{}")).optString("profile_pic_url", "");
        } catch (JSONException e) {
            return "";
        }
    }

    public static String userBio(Context context) {
        try {
            return new JSONObject(prefs(context).getString(KEY_USER, "{}")).optString("bio", "");
        } catch (JSONException e) {
            return "";
        }
    }

    public static boolean updateProfile(Context context, String name, String email, String mobile, String photo, String bio) {
        Context app = context.getApplicationContext();
        DISK_EXECUTOR.execute(() -> {
            try {
                String json = prefs(app).getString(KEY_USER, "{}");
                JSONObject user = new JSONObject(json);
                user.put("name", name);
                user.put("email", email);
                user.put("mobile", mobile);
                user.put("profile_pic_url", photo);
                user.put("bio", bio);
                prefs(app).edit().putString(KEY_USER, user.toString()).apply();
            } catch (JSONException ignored) {}
        });
        return true;
    }

    public static boolean isPrivateAccount(Context context) {
        try {
            return new JSONObject(prefs(context).getString(KEY_USER, "{}")).optBoolean("is_private", false);
        } catch (JSONException e) {
            return false;
        }
    }

    public static void setAccountPrivacy(Context context, boolean isPrivate) {
        Context app = context.getApplicationContext();
        DISK_EXECUTOR.execute(() -> {
            try {
                String json = prefs(app).getString(KEY_USER, "{}");
                JSONObject user = new JSONObject(json);
                user.put("is_private", isPrivate);
                prefs(app).edit().putString(KEY_USER, user.toString()).apply();
            } catch (JSONException ignored) {}
        });
    }

    public static void deleteAccount(Context context) {
        DISK_EXECUTOR.execute(() -> 
            prefs(context).edit().remove(KEY_USER).putBoolean("loggedIn", false).apply()
        );
    }

    // --- Listings ---

    public static void updateListingsCache(Context context, List<PolyGoApi.Listing> list) {
        JSONArray arr = new JSONArray();
        for (PolyGoApi.Listing l : list) {
            try {
                arr.put(ProductRecord.fromListing(l, userId(context)).toJson());
            } catch (JSONException ignored) {}
        }
        saveArray(context, KEY_LISTINGS, arr);
    }

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

    public static List<ListingEntity> listingsToEntities(List<ProductRecord> records) {
        List<ListingEntity> result = new ArrayList<>();
        if (records != null) {
            for (ProductRecord p : records) {
                result.add(p.toEntity());
            }
        }
        return result;
    }

    @Nullable
    public static ProductRecord getListing(Context context, String id) {
        for (ProductRecord item : getListings(context)) if (Objects.equals(item.id, id)) return item;
        return null;
    }

    public static ProductRecord addUserListing(Context context, String title, String category, String price, String description, String imageUri, String location) {
        return addUserListing(context, null, title, category, price, description, imageUri, location);
    }

    public static ProductRecord addUserListing(Context context, String serverId, String title, String category, String price, String description, String imageUri, String location) {
        JSONArray list = array(context, KEY_LISTINGS);
        String meetup = location == null || location.trim().isEmpty() ? "Near campus" : location.trim();
        String id = (serverId != null && !serverId.isEmpty()) ? serverId : UUID.randomUUID().toString();
        ProductRecord product = new ProductRecord(id, title, userName(context), price, "New", "0", meetup, R.drawable.bg_product_home, imageUri, category, description, true, true, userId(context));
        try {
            list.put(product.toJson());
            saveArray(context, KEY_LISTINGS, list);
        } catch (JSONException ignored) {}
        addNotification(context, "Your listing is live", title + " was added to the marketplace.");
        return product;
    }

    public static boolean markSold(Context context, String id) {
        Context app = context.getApplicationContext();
        DISK_EXECUTOR.execute(() -> {
            JSONArray list = array(app, KEY_LISTINGS);
            for (int i = 0; i < list.length(); i++) {
                try {
                    JSONObject o = list.getJSONObject(i);
                    if (id.equals(o.optString("id"))) {
                        o.put("available", false);
                        saveArray(app, KEY_LISTINGS, list);
                        return;
                    }
                } catch (JSONException ignored) {}
            }
        });
        return true;
    }

    public static boolean unarchiveListing(Context context, String id) {
        Context app = context.getApplicationContext();
        DISK_EXECUTOR.execute(() -> {
            JSONArray list = array(app, KEY_LISTINGS);
            for (int i = 0; i < list.length(); i++) {
                try {
                    JSONObject o = list.getJSONObject(i);
                    if (id.equals(o.optString("id"))) {
                        o.put("available", true);
                        saveArray(app, KEY_LISTINGS, list);
                        return;
                    }
                } catch (JSONException ignored) {}
            }
        });
        return true;
    }

    public static boolean isFavorite(Context context, String id) {
        return favoriteIds(context).contains(id);
    }

    public static void toggleFavorite(Context context, String id) {
        DISK_EXECUTOR.execute(() -> {
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
        } catch (JSONException e) {
            return null;
        }
    }

    @NonNull
    public static List<ThreadRecord> getThreads(Context context) {
        List<ThreadRecord> result = new ArrayList<>();
        JSONArray list = array(context, KEY_THREADS);
        for (int i = 0; i < list.length(); i++) {
            try {
                result.add(ThreadRecord.fromJson(list.getJSONObject(i)));
            } catch (JSONException ignored) {
            }
        }
        return result;
    }

    public static ThreadRecord getThread(Context context, String id) {
        for (ThreadRecord t : getThreads(context)) if (Objects.equals(t.id, id)) return t;
        return null;
    }

    public static void markThreadRead(Context context, String id) {
        DISK_EXECUTOR.execute(() -> {
            JSONArray threads = array(context, KEY_THREADS);
            for (int i = 0; i < threads.length(); i++) {
                try {
                    JSONObject t = threads.getJSONObject(i);
                    if (Objects.equals(id, t.optString("id"))) {
                        t.put("unread", false);
                        saveArray(context, KEY_THREADS, threads);
                        return;
                    }
                } catch (JSONException ignored) {
                }
            }
        });
    }

    public static void sendMessage(Context context, String threadId, String text) {
        DISK_EXECUTOR.execute(() -> {
            JSONArray threads = array(context, KEY_THREADS);
            for (int i = 0; i < threads.length(); i++) {
                try {
                    JSONObject t = threads.getJSONObject(i);
                    if (Objects.equals(threadId, t.optString("id"))) {
                        JSONArray msgs = t.optJSONArray("messages");
                        if (msgs == null) {
                            msgs = new JSONArray();
                        }
                        JSONObject m = new JSONObject();
                        m.put("sender", userName(context));
                        m.put("mine", true);
                        m.put("text", text);
                        m.put("time", System.currentTimeMillis());
                        msgs.put(m);
                        t.put("messages", msgs);
                        t.put("lastMessageTime", m.getLong("time"));
                        saveArray(context, KEY_THREADS, threads);
                        return;
                    }
                } catch (JSONException ignored) {
                }
            }
        });
    }

    public static void addReplyToThread(Context context, String id, String name, String text) {
        DISK_EXECUTOR.execute(() -> {
            JSONArray threads = array(context, KEY_THREADS);
            for (int i = 0; i < threads.length(); i++) {
                try {
                    JSONObject t = threads.getJSONObject(i);
                    if (Objects.equals(id, t.optString("id"))) {
                        JSONArray msgs = t.optJSONArray("messages");
                        if (msgs == null) {
                            msgs = new JSONArray();
                        }
                        JSONObject m = new JSONObject();
                        m.put("sender", name);
                        m.put("mine", false);
                        m.put("text", text);
                        m.put("time", System.currentTimeMillis());
                        msgs.put(m);
                        t.put("messages", msgs);
                        t.put("lastMessageTime", m.getLong("time"));
                        t.put("unread", true);
                        saveArray(context, KEY_THREADS, threads);
                        return;
                    }
                } catch (JSONException ignored) {
                }
            }
        });
    }

    // --- Transactions & Notifications ---

    public static void addTransaction(Context context, String listingId, String title, String amount) {
        DISK_EXECUTOR.execute(() -> {
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
        for (int i = list.length() - 1; i >= 0; i--) {
            try {
                result.add(TransactionRecord.fromJson(list.getJSONObject(i)));
            } catch (JSONException ignored) {
            }
        }
        return result;
    }

    public static TransactionRecord getTransaction(Context context, String id) {
        for (TransactionRecord t : getTransactions(context)) if (Objects.equals(t.id, id)) return t;
        return null;
    }

    public static boolean isActiveMeetupPhase(String status) {
        if (status == null) return false;
        String norm = status.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z]", "");
        return norm.equals("accepted")
                || norm.equals("pickup")
                || norm.equals("inprogress")
                || norm.equals("meetupscheduled");
    }

    public static TransactionRecord getEligibleReviewTransaction(Context context, String seller) {
        for (TransactionRecord t : getTransactions(context)) {
            if ("Completed".equalsIgnoreCase(t.status) && t.seller != null
                    && t.seller.equalsIgnoreCase(seller != null ? seller.trim() : "")) {
                return t;
            }
        }
        return null;
    }

    public static void updateTransactionStatus(Context context, String id, String status) {
        DISK_EXECUTOR.execute(() -> {
            JSONArray list = array(context, KEY_TRANSACTIONS);
            for (int i = 0; i < list.length(); i++) {
                try {
                    JSONObject o = list.getJSONObject(i);
                    if (Objects.equals(id, o.optString("id"))) {
                        o.put("status", status);
                        saveArray(context, KEY_TRANSACTIONS, list);
                        return;
                    }
                } catch (JSONException ignored) {
                }
            }
        });
    }

    public static void markTransactionReviewed(Context context, String id) {
        DISK_EXECUTOR.execute(() -> {
            JSONArray list = array(context, KEY_TRANSACTIONS);
            for (int i = 0; i < list.length(); i++) {
                try {
                    JSONObject o = list.getJSONObject(i);
                    if (Objects.equals(id, o.optString("id"))) {
                        o.put("reviewed", true);
                        saveArray(context, KEY_TRANSACTIONS, list);
                        return;
                    }
                } catch (JSONException ignored) {
                }
            }
        });
    }

    public static List<NotificationRecord> getNotifications(Context context) {
        List<NotificationRecord> result = new ArrayList<>();
        JSONArray list = array(context, KEY_NOTIFICATIONS);
        for (int i = list.length() - 1; i >= 0; i--) {
            try {
                result.add(NotificationRecord.fromJson(list.getJSONObject(i)));
            } catch (JSONException ignored) {
            }
        }
        return result;
    }

    public static void syncNotifications(Context context, List<PolyGoApi.Notification> server) {
        DISK_EXECUTOR.execute(() -> {
            try {
                JSONArray existing = array(context, KEY_NOTIFICATIONS);
                Map<String, JSONObject> localById = new LinkedHashMap<>();
                for (int i = 0; i < existing.length(); i++) {
                    JSONObject o = existing.optJSONObject(i);
                    if (o != null) localById.put(o.optString("id"), o);
                }
                JSONArray merged = new JSONArray();
                for (PolyGoApi.Notification n : server) {
                    String id = (n.id != null && !n.id.isEmpty()) ? n.id : String.valueOf(System.nanoTime());
                    JSONObject o = new JSONObject();
                    o.put("id", id);
                    o.put("title", n.title != null ? n.title : "Alert");
                    o.put("body", n.body != null ? n.body : "");
                    o.put("time", n.time);
                    o.put("read", n.read);
                    merged.put(o);
                    localById.remove(id);
                }
                for (JSONObject local : localById.values()) {
                    merged.put(local);
                }
                saveArray(context, KEY_NOTIFICATIONS, merged);
            } catch (JSONException ignored) {
            }
        });
    }

    public static void addNotification(Context context, String title, String body) {
        DISK_EXECUTOR.execute(() -> {
            JSONArray list = array(context, KEY_NOTIFICATIONS);
            try {
                long now = System.currentTimeMillis();
                for (int i = 0; i < list.length(); i++) {
                    JSONObject o = list.optJSONObject(i);
                    if (o == null) continue;
                    if (title.equals(o.optString("title")) && body.equals(o.optString("body"))
                            && now - o.optLong("time", 0) < 60_000L) {
                        return;
                    }
                }
                JSONObject o = new JSONObject();
                o.put("id", UUID.randomUUID().toString()); o.put("title", title); o.put("body", body);
                o.put("time", now); o.put("read", false);
                list.put(o); saveArray(context, KEY_NOTIFICATIONS, list);
            } catch (JSONException ignored) {}
        });
    }

    public static void markNotificationsRead(Context context) {
        DISK_EXECUTOR.execute(() -> {
            JSONArray list = array(context, KEY_NOTIFICATIONS);
            for (int i = 0; i < list.length(); i++) {
                try {
                    list.getJSONObject(i).put("read", true);
                } catch (JSONException ignored) {
                }
            }
            saveArray(context, KEY_NOTIFICATIONS, list);
        });
    }

    public static void markNotificationRead(Context context, String id) {
        DISK_EXECUTOR.execute(() -> {
            JSONArray list = array(context, KEY_NOTIFICATIONS);
            for (int i = 0; i < list.length(); i++) {
                try {
                    JSONObject o = list.getJSONObject(i);
                    if (Objects.equals(id, o.optString("id"))) {
                        o.put("read", true);
                        saveArray(context, KEY_NOTIFICATIONS, list);
                        return;
                    }
                } catch (JSONException ignored) {
                }
            }
        });
    }

    public static void addReport(Context context, String targetType, String targetId, String targetName, String reason, String details) {
        DISK_EXECUTOR.execute(() -> {
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
        DISK_EXECUTOR.execute(() -> {
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
        DISK_EXECUTOR.execute(() -> {
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
        DISK_EXECUTOR.execute(() -> saveArray(context, KEY_SEARCH_HISTORY, new JSONArray())); 
    }

    public static List<String> computeTrending(Context context) {
        java.util.LinkedHashMap<String, Integer> freq = new java.util.LinkedHashMap<>();
        List<String> history = getSearchHistory(context);
        int weight = history.size();
        for (String q : history) {
            if (q == null || q.trim().isEmpty()) continue;
            String key = q.trim();
            freq.put(key, freq.getOrDefault(key, 0) + weight);
            weight--;
        }
        for (ProductRecord p : getListings(context)) {
            if (p.category == null || p.category.trim().isEmpty()) continue;
            String key = p.category.trim();
            freq.put(key, freq.getOrDefault(key, 0) + 1);
        }
        List<java.util.Map.Entry<String, Integer>> entries = new ArrayList<>(freq.entrySet());
        entries.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        List<String> result = new ArrayList<>();
        for (java.util.Map.Entry<String, Integer> e : entries) {
            if (result.size() >= 10) break;
            result.add(e.getKey());
        }
        if (result.isEmpty()) Collections.addAll(result, TRENDING_SEARCHES);
        return result;
    }

    public static void saveDraft(Context context, String t, String c, String p, String d, String i, String l) {
        DISK_EXECUTOR.execute(() -> {
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
        DISK_EXECUTOR.execute(() -> {
            JSONArray list = array(context, KEY_DRAFTS); JSONArray next = new JSONArray();
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.optJSONObject(i); if (o != null && !Objects.equals(id, o.optString("id"))) next.put(o);
            }
            saveArray(context, KEY_DRAFTS, next);
        });
    }

    public static void setOnboardingSeen(Context context) { 
        DISK_EXECUTOR.execute(() -> prefs(context).edit().putBoolean(KEY_ONBOARDING, true).apply()); 
    }
    
    public static boolean hasSeenOnboarding(Context context) {
        return prefs(context).getBoolean(KEY_ONBOARDING, false);
    }
    
    public static void setMaintenanceMode(Context context, boolean on) { 
        DISK_EXECUTOR.execute(() -> prefs(context).edit().putBoolean(KEY_MAINTENANCE, on).apply()); 
    }

    public static boolean isBioLockEnabled(Context context) {
        return prefs(context).getBoolean(KEY_BIO_LOCK, false);
    }

    public static void setBioLockEnabled(Context context, boolean enabled) {
        DISK_EXECUTOR.execute(() -> prefs(context).edit().putBoolean(KEY_BIO_LOCK, enabled).apply());
    }

    public static boolean hasSeenMeetupDisclosure(Context context) {
        return prefs(context).getBoolean(KEY_MEETUP_DISCLOSURE, false);
    }

    public static void markMeetupDisclosureSeen(Context context) {
        prefs(context).edit().putBoolean(KEY_MEETUP_DISCLOSURE, true).apply();
    }

    public static String verificationStatus(Context context) {
        return prefs(context).getString(KEY_VERIFICATION_STATUS, "unverified");
    }

    public static void submitVerification(Context context) {
        DISK_EXECUTOR.execute(() -> prefs(context).edit().putString(KEY_VERIFICATION_STATUS, "pending").apply());
    }

    public static void approvePendingVerification(Context context) {
        DISK_EXECUTOR.execute(() -> prefs(context).edit().putBoolean(KEY_VERIFICATION, true).putString(KEY_VERIFICATION_STATUS, "approved").apply());
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
        for (int i = 0; i < list.length(); i++) {
            try {
                ReviewRecord r = ReviewRecord.fromJson(list.getJSONObject(i));
                if (name.equalsIgnoreCase(r.seller)) {
                    res.add(r);
                }
            } catch (JSONException ignored) {
            }
        }
        return res;
    }

    // --- IMMUTABLE RECORDS ---

    public static final class ProductRecord {
        @NonNull public final String id, title, seller, price, rating, reviewCount, distance, imageUri, category, description, ownerId;
        public final int imageRes; public final boolean owner, available;
        public boolean archived;

        public ProductRecord(@NonNull String id, @NonNull String title, @NonNull String seller, @NonNull String price, @NonNull String rating, @NonNull String reviewCount, @NonNull String distance, int imageRes, @NonNull String imageUri, @NonNull String category, @NonNull String description, boolean owner, boolean available, @NonNull String ownerId) {
            this.id = id; this.title = title; this.seller = seller; this.price = price; this.rating = rating; this.reviewCount = reviewCount; this.distance = distance; this.imageRes = imageRes; this.imageUri = imageUri; this.category = category; this.description = description; this.owner = owner; this.available = available; this.ownerId = ownerId;
        }

        public ProductRecord withOwnerStatus(boolean isOwner) {
            return new ProductRecord(id, title, seller, price, rating, reviewCount, distance, imageRes, imageUri, category, description, isOwner, available, ownerId);
        }

        public ListingEntity toEntity() {
            ListingEntity e = new ListingEntity(id, title, seller, price, rating, distance, imageUri.isEmpty() ? "" : imageUri,
                    category, description, ownerId, available, owner);
            e.reviewCount = reviewCount;
            return e;
        }

        public List<String> imageList() {
            if (imageUri.isEmpty()) return Collections.emptyList();
            List<String> res = new ArrayList<>();
            String[] parts = imageUri.split("\\|", -1);
            for (String part : parts) {
                if (part != null && !part.trim().isEmpty()) {
                    res.add(part.trim());
                }
            }
            return Collections.unmodifiableList(res);
        }

        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("title", title); o.put("seller", seller); o.put("price", price); o.put("rating", rating); o.put("review_count", reviewCount); o.put("distance", distance); o.put("imageRes", imageRes); o.put("imageUri", imageUri); o.put("category", category); o.put("description", description); o.put("owner", owner); o.put("available", available); o.put("owner_id", ownerId);
            return o;
        }

        public static ProductRecord fromJson(JSONObject o) {
            if (o == null) return null;
            String price = PriceFormatter.format(o.optString("price", "0")).replace("RM ", "");
            return new ProductRecord(o.optString("id", "0"), o.optString("title", "Item"), o.optString("seller", "User"), price, o.optString("rating", "4.5"), o.optString("review_count", "0"), o.optString("distance", "Near"), o.optInt("imageRes", R.drawable.bg_product_home), o.optString("imageUri", ""), o.optString("category", "General"), o.optString("description", ""), o.optBoolean("owner", false), o.optBoolean("available", true), o.optString("owner_id", "0"));
        }

        public static ProductRecord fromListing(PolyGoApi.Listing l, String currentUserId) {
            if (l == null) return null;
            String price = PriceFormatter.format(l.price).replace("RM ", "");
            boolean isOwner = currentUserId != null && currentUserId.equals(l.owner_id);
            String imageUri = l.image_url == null ? "" : l.image_url;
            String distance = l.distance == null ? "Near" : l.distance;
            ProductRecord rec = new ProductRecord(l.id, l.title, l.seller, price, l.rating, l.review_count, distance, R.drawable.bg_product_home, imageUri, l.category, l.description, isOwner, l.available, l.owner_id);
            rec.archived = l.archivedAt != null && !l.archivedAt.isEmpty();
            return rec;
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

        public static TransactionRecord fromTransaction(PolyGoApi.Transaction t) {
            if (t == null) return null;
            return new TransactionRecord(t.id, t.listingId, t.title, t.amount, t.status, t.location, t.seller, t.time, t.reviewed);
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

        public static ThreadRecord fromThread(PolyGoApi.Thread t) {
            if (t == null) return null;
            return new ThreadRecord(t.id, t.listingId, t.name, t.last_message, new JSONArray(), t.lastMessageTime, t.unread);
        }
    }

    public static final class ReviewRecord {
        @NonNull public final String id, seller, reviewer, comment; public final int stars; public final long time;

        public ReviewRecord(@NonNull String id, @NonNull String seller, @NonNull String reviewer, @NonNull String comment, int stars, long time) {
            this.id = id; this.seller = seller; this.reviewer = reviewer; this.comment = comment; this.stars = stars; this.time = time;
        }

        public static ReviewRecord fromJson(JSONObject o) {
            return new ReviewRecord(o.optString("id", ""), o.optString("seller", ""), o.optString("reviewer", ""), o.optString("comment", ""), o.optInt("stars", 0), o.optLong("time", 0));
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
