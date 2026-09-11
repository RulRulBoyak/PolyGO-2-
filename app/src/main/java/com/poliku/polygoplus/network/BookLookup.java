package com.poliku.polygoplus.network;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Looks up a scanned ISBN against the public Google Books API
 * (no key required) so the listing can be pre-filled.
 */
public final class BookLookup {

    private static final String ENDPOINT = "https://www.googleapis.com/books/v1/volumes?q=isbn:";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final OkHttpClient CLIENT = new OkHttpClient();

    private BookLookup() {
    }

    public interface Callback {
        void onResult(Book book);

        void onError(String message);
    }

    public static final class Book {
        public final String title;
        public final String author;
        public final String year;
        public final String pageCount;
        public final String description;
        public final String suggestedPrice;

        public Book(String title, String author, String year, String pageCount, String description, String suggestedPrice) {
            this.title = title;
            this.author = author;
            this.year = year;
            this.pageCount = pageCount;
            this.description = description;
            this.suggestedPrice = suggestedPrice;
        }
    }

    public static void search(String isbn, Callback callback) {
        if (isbn == null || isbn.trim().isEmpty()) {
            postError(callback, "Invalid ISBN");
            return;
        }
        final String query = isbn.trim();
        EXECUTOR.execute(() -> {
            try {
                Request request = new Request.Builder().url(ENDPOINT + query).build();
                try (Response response = CLIENT.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        postError(callback, "Google Books unavailable");
                        return;
                    }
                    Book book = parse(response.body().string());
                    if (book == null) {
                        postError(callback, "No book found");
                        return;
                    }
                    postResult(callback, book);
                }
            } catch (Exception error) {
                postError(callback, error.getMessage() == null ? "Network error" : error.getMessage());
            }
        });
    }

    private static Book parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray items = root.optJSONArray("items");
        if (items == null || items.length() == 0) {
            return null;
        }

        JSONObject item = items.getJSONObject(0);
        JSONObject info = item.optJSONObject("volumeInfo");
        if (info == null) {
            return null;
        }

        String title = info.optString("title", "").trim();

        JSONArray authors = info.optJSONArray("authors");
        String author = "";
        if (authors != null && authors.length() > 0) {
            StringBuilder joined = new StringBuilder();
            for (int i = 0; i < authors.length() && i < 2; i++) {
                if (joined.length() > 0) joined.append(", ");
                joined.append(authors.optString(i, "").trim());
            }
            author = joined.toString();
        }

        String published = info.optString("publishedDate", "");
        String year = published.length() >= 4 ? published.substring(0, 4) : "";

        String pageCount = info.has("pageCount") ? String.valueOf(info.optLong("pageCount")) : "";

        String description = info.optString("description", "").trim();
        if (description.length() > 300) {
            description = description.substring(0, 300).trim() + "...";
        }

        String price = "";
        JSONObject saleInfo = item.optJSONObject("saleInfo");
        if (saleInfo != null) {
            JSONObject listPrice = saleInfo.optJSONObject("listPrice");
            if (listPrice != null && listPrice.has("amount")) {
                double amount = listPrice.optDouble("amount");
                if (amount > 0) {
                    price = String.format(Locale.US, "%.2f", amount);
                }
            }
        }

        return new Book(title, author, year, pageCount, description, price);
    }

    private static void postResult(final Callback callback, final Book book) {
        MAIN.post(() -> callback.onResult(book));
    }

    private static void postError(final Callback callback, final String message) {
        MAIN.post(() -> callback.onError(message));
    }
}