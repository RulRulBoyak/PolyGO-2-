package com.poliku.polygoplus.network;

import com.google.android.gms.maps.model.LatLng;
import java.util.HashMap;
import java.util.Map;

/**
 * Phase 3: Campus Map Helper
 * Maps PKS landmark names to exact GPS coordinates for visualization.
 */
public final class CampusMapHelper {

    private static final Map<String, LatLng> LANDMARKS = new HashMap<>();

    static {
        // Approximate coordinates for Politeknik Kuching Sarawak (PKS) landmarks
        LANDMARKS.put("Block A", new LatLng(1.4831, 110.3475));
        LANDMARKS.put("Block B", new LatLng(1.4835, 110.3480));
        LANDMARKS.put("Block C", new LatLng(1.4838, 110.3485));
        LANDMARKS.put("Cafeteria", new LatLng(1.4842, 110.3470));
        LANDMARKS.put("Library", new LatLng(1.4828, 110.3488));
        LANDMARKS.put("Main Hall", new LatLng(1.4825, 110.3472));
        LANDMARKS.put("Mosque", new LatLng(1.4850, 110.3465));
        LANDMARKS.put("Sports Complex", new LatLng(1.4815, 110.3460));
        LANDMARKS.put("Student Centre", new LatLng(1.4833, 110.3490));
        LANDMARKS.put("Near campus", new LatLng(1.4830, 110.3470));
    }

    private CampusMapHelper() {}

    /**
     * Resolves a landmark name to a LatLng coordinate.
     * Defaults to the center of campus if not found.
     */
    public static LatLng getCoordinates(String landmark) {
        if (landmark == null) return LANDMARKS.get("Near campus");
        
        for (String key : LANDMARKS.keySet()) {
            if (landmark.toLowerCase().contains(key.toLowerCase())) {
                return LANDMARKS.get(key);
            }
        }
        
        return LANDMARKS.get("Near campus");
    }
}
