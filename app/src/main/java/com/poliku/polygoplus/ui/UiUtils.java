package com.poliku.polygoplus.ui;

import androidx.annotation.ColorRes;
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

    @ColorRes
    public static int categoryColor(String name) {
        if (name == null) return R.color.pks_blue;
        String n = name.toLowerCase();
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
        String n = name.toLowerCase();
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
}