package com.poliku.polygoplus.ui;

import androidx.annotation.DrawableRes;

import com.poliku.polygoplus.R;

/** Static UI helpers shared across screens. */
public final class UiUtils {

    private UiUtils() {
    }

    @DrawableRes
    public static int categoryIcon(String name) {
        if (name == null) return R.drawable.ic_category_tech;
        String n = name.toLowerCase();
        if (n.contains("food")) return R.drawable.ic_category_food;
        if (n.contains("drink")) return R.drawable.ic_category_drink;
        if (n.contains("tech") || n.contains("electron")) return R.drawable.ic_category_tech;
        if (n.contains("book")) return R.drawable.ic_category_books;
        if (n.contains("repair") || n.contains("print") || n.contains("laundry") || n.contains("serv")) return R.drawable.ic_category_repair;
        if (n.contains("fashion") || n.contains("cloth")) return R.drawable.ic_category_fashion;
        if (n.contains("home")) return R.drawable.ic_category_home;
        return R.drawable.ic_category_tech;
    }
}