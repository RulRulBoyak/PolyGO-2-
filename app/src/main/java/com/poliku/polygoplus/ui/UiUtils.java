package com.poliku.polygoplus.ui;

import android.content.Context;
import android.view.View;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;

import com.google.android.material.snackbar.Snackbar;
import com.poliku.polygoplus.R;
import com.poliku.polygoplus.api.PolyGoApi;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Static UI helpers shared across screens. */
public final class UiUtils {
    private static final Map<String, Integer> SERVER_CATEGORY_ICONS =
            new ConcurrentHashMap<>();

    private UiUtils() {
    }

    @DrawableRes
    public static int categoryIcon(String name) {
        if (name == null) return R.drawable.ic_category_tech;
        Integer configured = SERVER_CATEGORY_ICONS.get(name.toLowerCase(Locale.ROOT));
        if (configured != null) return configured;
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("food")) return R.drawable.ic_category_food;
        if (n.contains("drink")) return R.drawable.ic_category_drink;
        if (n.contains("tech") || n.contains("electron")) return R.drawable.ic_category_tech;
        if (n.contains("book")) return R.drawable.ic_category_books;
        if (n.contains("repair") || n.contains("fix")) return R.drawable.ic_category_repair;
        if (n.contains("laundry") || n.contains("wash")) return R.drawable.ic_category_laundry;
        if (n.contains("print")) return R.drawable.ic_category_printing;
        if (n.contains("serv")) return R.drawable.ic_category_service;
        if (n.contains("fashion") || n.contains("cloth")) return R.drawable.ic_category_fashion;
        if (n.contains("home")) return R.drawable.ic_category_home;
        if (n.contains("deliver") || n.contains("ship") || n.contains("courier")) return R.drawable.ic_category_delivery;
        return R.drawable.ic_category_tech;
    }

    /** Maps the server's fixed icon catalogue without resolving arbitrary resources. */
    @DrawableRes
    public static int categoryIconKey(String key) {
        if (key == null) return R.drawable.ic_category_tech;
        switch (key) {
            case "ic_category_food": return R.drawable.ic_category_food;
            case "ic_category_drink": return R.drawable.ic_category_drink;
            case "ic_category_fashion": return R.drawable.ic_category_fashion;
            case "ic_category_books": return R.drawable.ic_category_books;
            case "ic_category_repair": return R.drawable.ic_category_repair;
            case "ic_category_home": return R.drawable.ic_category_home;
            case "ic_category_laundry": return R.drawable.ic_category_laundry;
            case "ic_category_delivery": return R.drawable.ic_category_delivery;
            case "ic_category_service": return R.drawable.ic_category_service;
            case "ic_category_printing": return R.drawable.ic_category_printing;
            case "ic_category_tech": return R.drawable.ic_category_tech;
            default: return R.drawable.ic_category_tech;
        }
    }

    public static void rememberCategoryIcons(Context context,
                                             List<PolyGoApi.Category> categories) {
        if (context == null || categories == null) return;
        for (PolyGoApi.Category category : categories) {
            if (category == null || category.name == null || category.icon_res == null) continue;
            SERVER_CATEGORY_ICONS.put(category.name.toLowerCase(Locale.ROOT),
                    categoryIconKey(category.icon_res));
        }
    }

    @ColorRes
    public static int categoryColor(String name) {
        if (name == null) return R.color.pks_blue;
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("food")) return R.color.cat_food;
        if (n.contains("drink") || n.contains("water") || n.contains("coffee")) return R.color.cat_drink;
        if (n.contains("tech") || n.contains("electron")) return R.color.cat_tech;
        if (n.contains("book")) return R.color.cat_books;
        if (n.contains("fashion") || n.contains("cloth")) return R.color.cat_fashion;
        if (n.contains("home")) return R.color.cat_home;
        if (n.contains("repair")) return R.color.cat_repair;
        if (n.contains("serv") || n.contains("print") || n.contains("laundry") || n.contains("clean")
                || n.contains("deliver") || n.contains("less") || n.contains("tutor")) {
            return R.color.cat_service;
        }
        return R.color.pks_blue;
    }

    @ColorRes
    public static int categoryTint(String name) {
        if (name == null) return R.color.soft_blue;
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("food")) return R.color.soft_food;
        if (n.contains("drink") || n.contains("water") || n.contains("coffee")) return R.color.soft_drink;
        if (n.contains("tech") || n.contains("electron")) return R.color.soft_tech;
        if (n.contains("book")) return R.color.soft_books;
        if (n.contains("fashion") || n.contains("cloth")) return R.color.soft_fashion;
        if (n.contains("home")) return R.color.soft_home;
        if (n.contains("repair")) return R.color.soft_repair;
        if (n.contains("serv") || n.contains("print") || n.contains("laundry") || n.contains("clean")
                || n.contains("deliver") || n.contains("less") || n.contains("tutor")) {
            return R.color.soft_service;
        }
        return R.color.soft_blue;
    }

    /** Shows a short themed snackbar anchored to the given view. */
    public static void snackbar(View anchor, String message) {
        if (anchor == null || anchor.getContext() == null) return;
        Snackbar.make(anchor, message, Snackbar.LENGTH_SHORT).show();
    }

    /** Resource-id overload of {@link #snackbar(View, String)}. */
    public static void snackbar(View anchor, int messageRes) {
        if (anchor == null || anchor.getContext() == null) return;
        snackbar(anchor, anchor.getContext().getString(messageRes));
    }

    /** Shows a short themed snackbar pre-styled as an error/feedback message. */
    public static void snackbarError(View anchor, String message) {
        if (anchor == null || anchor.getContext() == null) return;
        Snackbar snackbar = Snackbar.make(anchor, message, Snackbar.LENGTH_SHORT);
        snackbar.setBackgroundTint(anchor.getContext().getColor(R.color.airbnb_ink));
        snackbar.setTextColor(anchor.getContext().getColor(R.color.white));
        snackbar.show();
    }

    /** Resource-id overload of {@link #snackbarError(View, String)}. */
    public static void snackbarError(View anchor, int messageRes) {
        if (anchor == null || anchor.getContext() == null) return;
        snackbarError(anchor, anchor.getContext().getString(messageRes));
    }
}
