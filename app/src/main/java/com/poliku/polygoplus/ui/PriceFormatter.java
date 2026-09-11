package com.poliku.polygoplus.ui;

import java.util.Locale;

/**
 * Principal Rule 3.3: Consistent UI Presentation
 * Centralized utility for RM currency formatting across the PKS ecosystem.
 */
public final class PriceFormatter {
    private PriceFormatter() {}

    /**
     * Formats a raw string or number into a professional RM price string.
     * Handles "RM", "rm", or pure numbers gracefully.
     */
    public static String format(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "RM 0.00";
        
        // Strip everything except numbers and dots
        String cleaned = raw.replaceAll("[^0-9.]", "");
        if (cleaned.isEmpty()) return "RM 0.00";

        try {
            double price = Double.parseDouble(cleaned);
            return String.format(Locale.US, "RM %.2f", price);
        } catch (NumberFormatException e) {
            return "RM " + cleaned;
        }
    }
}
