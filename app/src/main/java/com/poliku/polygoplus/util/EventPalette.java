package com.poliku.polygoplus.util;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared event card palette. Mirrors the admin panel's theme grid exactly so the
 * Android card and the web preview render identically.
 */
public final class EventPalette {

    private static final Map<String, String[]> THEMES = new HashMap<>();

    static {
        THEMES.put("default", new String[]{"#0D47A1", "#1E88E5"});
        THEMES.put("ocean", new String[]{"#0077B6", "#00B4D8"});
        THEMES.put("sunset", new String[]{"#E25822", "#FFB03A"});
        THEMES.put("forest", new String[]{"#14532D", "#4ADE80"});
        THEMES.put("royal", new String[]{"#4C1D95", "#7C3AED"});
        THEMES.put("lavender", new String[]{"#5E35B1", "#9575CD"});
        THEMES.put("pink", new String[]{"#AD1457", "#F06292"});
        THEMES.put("midnight", new String[]{"#263238", "#546E7A"});
    }

    private EventPalette() {
    }

    public static int[] gradientColors(String theme) {
        String[] colors = THEMES.get(theme == null ? "default" : theme);
        if (colors == null) colors = THEMES.get("default");
        return new int[]{parseHex(colors[0]), parseHex(colors[1])};
    }

    public static int accentColor(String theme, String accentHex, int fallbackSuffix) {
        int[] base = gradientColors(theme);
        int fallback = base[fallbackSuffix];
        return validHex(accentHex) ? parseHex(accentHex) : fallback;
    }

    public static boolean validHex(String hex) {
        return hex != null && hex.matches("#[0-9a-fA-F]{6}");
    }

    public static int parseHex(String hex) {
        try {
            return Color.parseColor(hex);
        } catch (IllegalArgumentException e) {
            return Color.parseColor("#0D47A1");
        }
    }

    /** Picks black or white text for a solid chip background (same lightness rule as the admin JS). */
    public static int contrastColor(int color) {
        double r = Color.red(color) / 255.0;
        double g = Color.green(color) / 255.0;
        double b = Color.blue(color) / 255.0;
        double lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        return lum > 0.5 ? 0xFF111827 : 0xFFFFFFFF;
    }

    public static GradientDrawable gradient(String theme) {
        int[] colors = gradientColors(theme);
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
    }
}